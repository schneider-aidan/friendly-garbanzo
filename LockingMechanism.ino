#include "BluetoothSerial.h"

BluetoothSerial SerialBT;

const int RELAY_PIN = 22;

// Change depending on relay type
const int RELAY_ON = LOW;     // many relay boards
const int RELAY_OFF = HIGH;

void setup() {
  Serial.begin(115200);

  pinMode(RELAY_PIN, OUTPUT);
  digitalWrite(RELAY_PIN, RELAY_OFF);

  SerialBT.begin("ESP32_Lock");
  Serial.println("Bluetooth Ready");
}

void loop() {

  if (SerialBT.available()) {
    String cmd = SerialBT.readStringUntil('\n');
    cmd.trim();

    Serial.print("Received: ");
    Serial.println(cmd);

    if (cmd == "UNLOCK") {
      unlockDoor();
    }

    // if (cmd == "1") {
    //   digitalWrite(RELAY_PIN, RELAY_ON);
    // }

    // if (cmd == "0") {
    //   digitalWrite(RELAY_PIN, RELAY_OFF);
    // }
  }
}

void unlockDoor() {
  Serial.println("Unlocking...");
  digitalWrite(RELAY_PIN, RELAY_ON);
  delay(5000);   // unlock for 5 sec
  digitalWrite(RELAY_PIN, RELAY_OFF);
  Serial.println("Locked");
}
