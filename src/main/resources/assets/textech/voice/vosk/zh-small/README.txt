Place the unpacked Vosk Chinese small model contents in this directory for the with-voice / voice extras build.

Expected contents include conf/, am/, graph/, ivector/ and related model files.
Recommended model: vosk-model-small-cn-0.22 from https://alphacephei.com/vosk/models

Release packaging:
- Main TeXTech jar excludes this folder (keeps the download small).
- Optional companion jar classifier "voice" (modid textechvoice) ships these files for players.
- Development (runClient) still uses this source tree in-repo; no companion jar required.

The mod can also load an external model by setting voice.sttModel to the model directory path,
or use voice.sttMode=http without any local Vosk model.
