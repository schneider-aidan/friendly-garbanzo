Authors: Thomas Reljin, Aiden Schneider, Elijah Routh

Project Description:
Our gesture lock app and locking device can be used to create a unique security experience that requires using gestures and audio to unlock doors, cabinets, etc.

Required Hardware:
The following were bought on Amazon - links attached

Relay
https://www.amazon.com/AEDIKO-Channel-Optocoupler-Isolation-Support/dp/B095YFJ69T/ref=sr_1_3?crid=K7LK36Z2F6P4&dib=eyJ2IjoiMSJ9.xM0gVkZuRCa_k3Ea0PvQDMvNtu0X3AgFuuET0DFNeVTvFAiPrsxX7DCuU5fwJihjalfdx1A-wtPJIxSvy3M2olrhIBow9JLPmTndbjtgOsvUJKIh13uVrQ64FXWaZcOY_F407TdOxI-B-Vt7IuRpKIdbfD3Lc_gwY13Jh0RKC_7sV_wiGFdrICJ_dor4iiQ5QjAcKrJl0qOuloscjQ6nO5xs6Zwl51XpVyM4HsxPQnA.fQnz2sajnUoURS0tJwDo58yegIGOUVm9IelJJxaPxrs&dib_tag=se&keywords=12%2Bvolt%2Brelay%2Barduino&qid=1777732321&sprefix=12%2Bvolt%2Brelay%2Barduino%2Caps%2C127&sr=8-3&th=1

Solenoid Lock
https://www.amazon.com/QWORK-Electromagnetic-Solenoid-Assembly-Cabinet/dp/B093H6GD4V/ref=sr_1_5?crid=1233HGMZPYQXM&dib=eyJ2IjoiMSJ9.S-DZEqPGaqeztLpW2WHrbsa_ql3u3h2K_1s5ba5QhFGa_R2f43mxzpdl4PsofNi1oJ7xGcDsziVBs9iuObFYFdNZARlNceu0E0cfAQ09fB8udaajJnMMX4SqmYeSDuRDlJtKMCTBjujXN22JZvH4liW2EkPNe1KRvCsSkBd8Gj6nbM8uDyG0RPd8Sgv1dEWcXStilmEfCJFlunqNuEiV4gbeUi8jRYhCuqTqrXcSaM8m4nNBUKVe_vi70gQMVjM1RPQg9YqTOQGeROVkTeBbkntaR9lcTpkZxn_S9NvPtkU.7mbJvyFVrue60SXpM9dcDt3rHvbK39okXeMhV7g_pEQ&dib_tag=se&keywords=solenoid%2Block&qid=1777733061&sprefix=solenoid%2Block%2Caps%2C127&sr=8-5&th=1

12 V Power Supply
https://www.amazon.com/JOVNO-100-240V-Converter-Transformer-5-5x2-5mm/dp/B0875WMYCX/ref=sr_1_1?crid=2RZW6C31N3E0X&dib=eyJ2IjoiMSJ9.ry-AHtMszPw8F-QfMIxi-pToDTYiqI7yrikY8lQWOKDQQhGDXFPEU40IOHNa3LK_OFD8BJu7_Rr6iisXk41qyh1mP9G7XJjWylH7HOS8NPicGu7EPz9iRci0ud8EkgAa2LGlxkULnbhGCAh7EFCJhJw3bUdBQSWE6Qi-fikUV9Y03j7x5at8YLFYmM8DX2MsgNfoGEvD21QgMkFYOc1JwwCo0eTe_Q1d_gsDy8m5P8E.6FVpiN-1KiA4RUWlzrXWukRDDbgkbUlr-N7hMzW1uug&dib_tag=se&keywords=12%2Bvolt%2Bwall%2Barduino&qid=1777733108&sprefix=12%2Bvolt%2Bwall%2Barduino%2Caps%2C120&sr=8-1&th=1

The following were provided by the instructor:
ESP32
PIR sensor

Required Software:
MediaPipe
https://ai.google.dev/edge/mediapipe/solutions/vision/pose_landmarker/android

Directions:
Open the project folder in Android Studio and install app onto android app.
Assemble ESP32, PIR sensor, Relay, and Solenoid Lock.
Install ESP32 with Arduino code.
Open the app.
Open the settings page.
Note - The password is “password.”
Connect to ESP32 via Bluetooth.
Note - the app will not allow the user to attempt a gesture password without a bluetooth connection.
Add any desired poses and set a new gesture password.
Set a new audio password.
Return to the home screen and attempt gesture and audio password.
Note - Once both passwords are entered correctly the first time the lock will unlock for 5 seconds and then relock.
Note - To reset after failed attempt or after successfully entering both the user must hit the reset button.

