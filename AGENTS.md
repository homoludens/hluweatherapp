Android sdk location:
export ANDROID_HOME=/home/homoludens/Android/Sdk
export ANDROID_SDK_ROOT=/home/homoludens/Android/Sdk

simpler code is good.
simple architecture is good.

when some confirmation for folder access doesn't come in about 10 minutes, contionue without it and log it to UNCONFIRMED_REQUESTS.md

credentials are defined in .env file

build only google debug variant. i will ask for signed release when we are ready.

run the narrowest relevant test for the change during iteration. Run broader test suites or the Google debug build only when the change affects multiple areas, when targeted tests are insufficient, or as the final verification before reporting completion.
