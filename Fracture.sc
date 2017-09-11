Fracture { var <server, <in, <bufferSize, <loopRecord, <>ampThresh, <>pitchThresh, <>minDur, <>maxDur;
	var <buffer, controlBus, record, collectchips, chips, <notes, collectRoutines, <>defaultSynth, <>writeInterval = 0.02, <>clearInterval = 0.2, <isEmpty, <isLocked, <filePath;

	*new { arg server, in = 0, bufferSize = 60, loopRecord = true, ampThresh = 0.01, pitchThresh = 0.9, minDur = 0.1, maxDur = inf;
		^super.newCopyArgs(server, in, bufferSize, loopRecord, ampThresh, pitchThresh, minDur, maxDur).init
	}

	*load { arg server, path;
		^super.new.initLoad(server, path)
	}

	init {

		server = server ?? Server.default;

		controlBus = Array.fill(5, {|i| Bus.control(server, 1)});
		buffer = Buffer.alloc(server, server.sampleRate * bufferSize);

		chips = [];
		notes = [];

		defaultSynth = FractureSynth.new(this);
		isEmpty = true;
		isLocked = false
	}

	initLoad {arg argserver, argpath;
		var path, loadfunc, readdone;

		server = argserver ?? Server.default;

		loadfunc = {arg path;
			var readdone;
			readdone = {|buffer| bufferSize = buffer.numFrames/buffer.sampleRate};
			buffer = Buffer.read(server, path +/+ "/buffer.aiff", action: readdone);

			chips = Object.readArchive(path +/+ "chips.txt");
			chips = chips.do{|chip| chip[\fracture] = this};

			notes = try{chips.collect({|x| x[\pitch]}).asSet.asArray.sort};

			defaultSynth = FractureSynth.new(this);

			isEmpty = false;
			isLocked = true;
			filePath = path;
		};

		if (argpath.isNil)
		{FileDialog(loadfunc, {}, 2, 0, true)}
		{loadfunc.(argpath)}

	}

	openFunctions { var addchip, draftpitch, peakAmp = 0, draftchip = (), writechips, clearchips;

		addchip = { arg chip;
			if ( [\start, \end, \pitch, \cycle, \peakAmp].any({|x| chip[x] == nil}) || (chip == ()) || (chip == nil) )
			{}
			{
				var chipDur;
				chipDur = (chip[\end] - chip[\start])/buffer.sampleRate;
				if ((chips.any({|x| x == chip})).not && (chipDur > minDur) && (chipDur < maxDur))
				{
					var newchip;
					newchip = chip.putPairs([\type, \fchip, \fracture, this]);
					chips = chips.add(newchip);
				}
				{}
			}
		};

		writechips = Routine {
			var pos, amp, hasFreq, pitch, currentcycle;

			inf.do {
				pos = controlBus[0].getSynchronous;
				amp = controlBus[1].getSynchronous;
				hasFreq = controlBus[2].getSynchronous;
				pitch = controlBus[3].getSynchronous.cpsmidi.round(1);
				currentcycle = controlBus[4].getSynchronous;

				if ((amp ?? 0 > ampThresh) && (hasFreq ?? 0 > pitchThresh))
				{
					if (amp > peakAmp) {peakAmp = amp} {};
					if (pitch == draftpitch)
					{
						draftchip = draftchip.putPairs([\end, pos, \peakAmp, peakAmp])
					}
					{
						if (draftchip.notNil, {addchip.(draftchip)}, {});
						draftpitch = pitch;
						draftchip = (\pitch: draftpitch, \start: pos, \cycle: currentcycle);
						peakAmp = amp;
					}
				}
				{
					if (draftchip.notNil) {addchip.(draftchip)} {}
				};

				writeInterval.wait;
			}
		};

		clearchips = Routine {
			var pos, currentcycle;

			inf.do {
				pos = controlBus[0].getSynchronous;
				currentcycle = controlBus[4].getSynchronous;
				if (currentcycle == 0 || chips == [()] || chips == [] || chips == nil)
				{}
				{
					chips = chips.reject{|x| (x[\start] < pos) && (x[\cycle] < currentcycle)};
					notes = try{chips.collect({|x| x[\pitch]}).asSet.asArray.sort} {[]};
				};

				clearInterval.wait;
			}
		};

		collectRoutines = [writechips, clearchips];
	}


	open {
		if (isLocked == true) {^'Fracture locked.'};
		if (isEmpty == true) {
			this.openFunctions;

			collectRoutines.do (_.play(SystemClock));

			record = {
				arg running = 1;
				var soundin, trig = 1, pos = 0, amp, freq, hasFreq, bufLength, cycle;

				soundin = if (in.isInteger) {SoundIn.ar(in)} {In.ar(in)};

				//pos = Phasor.ar(0, BufRateScale.kr(buffer.bufnum), 0, BufFrames.kr(buffer.bufnum));
				trig = Impulse.ar(buffer.sampleRate / buffer.numFrames);
				pos = Sweep.ar(trig, buffer.sampleRate * running);

				amp = Amplitude.kr(soundin);
				# freq, hasFreq = Pitch.kr(soundin);

				cycle = PulseCount.ar(trig);

				Out.kr(controlBus[0], pos);
				Out.kr(controlBus[1], amp);
				Out.kr(controlBus[2], hasFreq);
				Out.kr(controlBus[3], freq);
				Out.kr(controlBus[4], cycle);

				BufWr.ar(soundin, buffer.bufnum, pos, 1);
				0.0
			}.play(addAction: \addToTail);

			isEmpty = false;
		}
		{
			record.set(\running, 1);
		};

		if (loopRecord == false) {
			{
				bufferSize.wait;
				this.lock
			}.fork(SystemClock)
		};
	}

	close {
		if (isLocked == true) {^this};
		if (isEmpty == false) {
			record.set(\running, 0);
			notes = try{chips.collect({|x| x[\pitch]}).asSet.asArray.sort}
		}
	}

	save { arg path;
		var savefunc;

		savefunc = { arg savepath;
			var chiparchive = [];

			filePath = savepath;

			chips.do{ arg chip;
				var newchip = ();
				chip.keysValuesDo({ |key, value|
					if (key != 'fracture')
					{newchip.put(key, value)}
				});
				chiparchive = chiparchive.add(newchip)
				};

			File.mkdir(savepath);
			buffer.write((savepath +/+ "/buffer.aiff").standardizePath);
			chiparchive.writeArchive(savepath +/+ "/chips.txt");
			//File.use((path +/+ "/chips.txt").standardizePath, "w", (_.write(chips.asString)));

		};

		if (path.isNil)
		{FileDialog.new(savefunc, {}, 0, 1, true)}
		{savefunc.(path)};
	}

	clear {
		record !? {record.free; record = nil};
		collectRoutines !? {collectRoutines.do(_.stop); collectRoutines = nil};
		buffer.zero;
		chips = [];
		notes = [];
	}

	lock {
		record !? {record.free; record = nil};
		collectRoutines !? {collectRoutines.do(_.stop); collectRoutines = nil};
		isLocked = true;
	}

	free {
		this.clear;
		buffer !? (_.free);
		^super.free;
	}

	getChips {
		var chipscopy = [];
		chips.do{ arg chip;
			var newchip = ();
			chip.keysValuesDo({ |key, value|
				if (['pitch', 'peakAmp', 'cycle', 'start', 'end', 'type'].any({|x| x == key}))
				{newchip.put(key, value)}
			});
			chipscopy = chipscopy.add(newchip)
		};

		chipscopy = chipscopy.do{ |chip|
			chip[\fracture] = this;
			chip[\pan] = 2;
		};
		^chipscopy
	}

	chips {
		^this.getChips
	}

	noteChips {arg note;
		var chipscopy, notechips;

		chipscopy = this.getChips();

		if (note.isArray)
		{notechips = chipscopy.select({|chip| note.any({|n| n == chip[\pitch]})})}
		{notechips = chipscopy.select({|chip| chip[\pitch] == note})};

		^notechips;
	}

	playNote {arg note;
		var notechips = this.noteChips(note);
		notechips.choose.play;
		}

}
