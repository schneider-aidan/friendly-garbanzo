#include "BluetoothSerial.h"
#include "esp_sleep.h"

BluetoothSerial SerialBT;

// Pins
const int builtInLedPin = 2;
const int relayPin = 22;
const int irSensorPin = 33;   // ADC-capable pin for analog threshold

// Relay logic
const int RELAY_ON = LOW;     // many relay modules are active LOW
const int RELAY_OFF = HIGH;

// IR threshold
const int irThreshold = 1500; // adjust after testing

// Sleep settings
const unsigned long sleepTimeoutMs = 60000;
unsigned long lastActivityTime = 0;

// State tracking
bool motionDetected = false;

void setup() {
  Serial.begin(115200);
  delay(300);

  pinMode(builtInLedPin, OUTPUT);
  pinMode(relayPin, OUTPUT);

  digitalWrite(relayPin, RELAY_OFF);
  digitalWrite(builtInLedPin, HIGH);   // awake = LED ON

  SerialBT.begin("ESP32_Lock");
  Serial.println("Bluetooth ready");

  int initialValue = analogRead(irSensorPin);
  Serial.print("Initial IR value: ");
  Serial.println(initialValue);

  motionDetected = initialValue > irThreshold;
  Serial.print("Initial IR state: ");
  Serial.println(motionDetected ? "DETECTED" : "CLEAR");

  lastActivityTime = millis();
}

void loop() {
  handleBluetoothCommands();
  readInfraredSensor();
  checkSleepTimeout();
  delay(50);
}

void handleBluetoothCommands() {
  if (!SerialBT.available()) return;

  String incoming = SerialBT.readStringUntil('\n');
  incoming.trim();

  Serial.print("Received: ");
  Serial.println(incoming);

  lastActivityTime = millis();

  if (incoming == "UNLOCK") {
    unlockDoor();
  } else if (incoming == "SLEEP") {
    goToSleep();
  }
}

bool isIrActive() {
  int value = analogRead(irSensorPin);
  Serial.print("IR Value: ");
  Serial.println(value);
  return value > irThreshold;
}

void readInfraredSensor() {
  bool nowDetected = isIrActive();

  if (nowDetected != motionDetected) {
    motionDetected = nowDetected;
    lastActivityTime = millis();

    if (motionDetected) {
      SerialBT.println("IR_DETECTED");
      Serial.println("Sent: IR_DETECTED");
    } else {
      SerialBT.println("IR_CLEAR");
      Serial.println("Sent: IR_CLEAR");
    }
  }
}

void unlockDoor() {
  Serial.println("Unlocking...");
  digitalWrite(relayPin, RELAY_ON);
  delay(5000);
  digitalWrite(relayPin, RELAY_OFF);
  Serial.println("Locked");
}

void checkSleepTimeout() {
  if (millis() - lastActivityTime > sleepTimeoutMs) {
    if (!isIrActive()) {
      Serial.println("Idle timeout reached");
      goToSleep();
    } else {
      Serial.println("IR still active, staying awake");
      lastActivityTime = millis();
    }
  }
}

void goToSleep() {
  if (isIrActive()) {
    Serial.println("IR active, aborting sleep");
    lastActivityTime = millis();
    return;
  }

  Serial.println("Entering light sleep...");
  digitalWrite(builtInLedPin, LOW);   // sleeping = LED OFF

  delay(200);

  // Wake after a short time and check analog threshold again
  esp_sleep_enable_timer_wakeup(1000000); // 1 sec
  esp_light_sleep_start();

  Serial.println("Woke from light sleep");
  digitalWrite(builtInLedPin, HIGH);  // awake = LED ON
  lastActivityTime = millis();
}
