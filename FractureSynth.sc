FractureSynth {
	classvar runningindex = 0;
	var <fracture, synth, index;

	*initClass {
		Event.addEventType(\fchip,

			Event.default.eventTypes[\note] <> {|server|
				if(~instrument == 'default') {~instrument = ~fracture.defaultSynth.name};
				server
		})
	}

	*new { arg fracture, numChannels = 2, pan = 0, gain = -20, normalize = 0.7, env = [0.2, 0.2, \lin], rate = 1;
    ^super.new.init(fracture, numChannels, pan, gain, normalize, env, rate)
	}

	init {arg argfracture, argnumChannels, argpan, arggain, argnormalize, argenv, argrate;
		var synthname, fracture = argfracture, index;

		index = runningindex;
		runningindex = runningindex + 1;
		synthname = ("FractureSynth" ++ index);

		synth = SynthDef( synthname, {
			arg pitch, start, end, peakAmp, offset = 0, clip = 1, zoom = 0, amp = 1, rate = 1, pan = -2, out = 0;

			var numChannels = argnumChannels, envin = argenv, baserate = argrate, normalize = argnormalize, panin = argpan, gain = arggain;

			var bufsize = fracture.buffer.numFrames,
			bufnum = fracture.buffer.bufnum,
			samplerate = fracture.server.sampleRate,
			fracnotes = fracture.notes,
			playstart, playend, zoomadjust, len, env, pos, basepan, playpan, preamp, monosig, sig;

			playstart = start + offset;
			playend = start + ((end - start)*clip);

			zoomadjust = (playend - playstart) * zoom / 2;
			playstart = playstart + zoomadjust;
			playend = playend - zoomadjust;

			//len = (sustain ?? (end - start)/samplerate)/(baserate.abs);
			len = ((playend - playstart)/samplerate)/(baserate.abs);

			if (envin.isArray)
			{
				var attackratio, releaseratio, attack, release, curve;
				attackratio = envin[0] ?? 0; releaseratio = envin[1] ?? 0;
				if (attackratio + releaseratio > 1) {attack = 0; release = 0}
				{
					attack = attackratio * len;
					release = releaseratio * len;
				};
				curve = envin[2] ?? 'lin';
				env = Env.linen(attack, len - attack - release, release, 1, curve)
			}
			{
				switch (envin,
					'sine', {env = Env.sine(len)},
					'triangle', {env = Env.triangle(len)},
					{env = 1}
				)
			};

			if (baserate > 0)
			{pos = Sweep.ar(0, samplerate*baserate*rate) + playstart}
			{pos = Sweep.ar(0, samplerate*baserate*rate) + playend};

			//{Line.ar(end, end - sustain*samplerate, len, doneAction: 2)};

			//pos = Phasor.ar(0, BufRateScale.kr(bufnum) * rate,
			//	if (rate > 0) {start} {end},
			//	if (rate > 0) {start + sustain*samplerate} {end - sustain*samplerate});

			if (panin.isNumber) {basepan = panin} {
				switch (panin,
					'scatter', {basepan = Rand(-1, 1)},
					'pos', {basepan = pos/bufsize * 2 - 1},
					'pitch', {basepan = (pitch - fracnotes[0])/(fracnotes[fracnotes.size-1] - fracnotes[0]) * 2 - 1},
					{basepan = 0}
				)
			};


			playpan = Select.kr(InRange.kr(pan, -1, 1), [basepan, pan]);

			if (normalize > 0)
			{preamp = ((1/peakAmp - 1)*normalize + (gain.dbamp ?? 1))}
			{preamp = (gain.dbamp ?? 1)};

			monosig = BufRd.ar(1, bufnum, pos) * EnvGen.ar(env, Impulse.kr(1/len)) * preamp * amp;

			if (numChannels > 1)
			{sig = Pan2.ar(monosig, playpan)}
			{sig = monosig};

			Line.kr(0, 0, len, doneAction: 2);
			Out.ar(out, sig)
		}).add;

		^synth

	}


}

/*

		replaceArgs = {arg newArgs, initArgs;
			if (newArgs.isNil,
				{initArgs},
				{
					var initArgNames, initArgValues, newArgNames, newArgValues, replaceArgValues;
					initArgNames = initArgs.select({|item, i| i.even});
					initArgValues = initArgs.select({|item, i| i.odd});
					newArgNames = newArgs.select({|item, i| i.even});
					newArgValues = newArgs.select({|item, i| i.odd});
					replaceArgValues = initArgNames.collect({ |item, i|
						if (newArgNames.includes(item),
							{
								var newArgIndex = newArgNames.detectIndex(_==item);
								var newArgValue = newArgValues[newArgIndex];
								newArgValue;
							},
							{initArgValues[i]}
						)
					});
					[initArgNames, replaceArgValues].lace
				}
			)
		};