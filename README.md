# Whatsapp Web Request Analyzer

This program was developed on top of [Cobalt](https://github.com/Auties00/Cobalt) and is fully written in Kotlin.

The goal of this program is to analyze both raw json and encrypted binary requests sent by WhatsappWeb's Web client to WhatsappWeb's 
secure WebSocket and vice-versa. This is very useful if you want to contribute to WhatsappWeb4j by adding new features.

### How to run

Follow these steps to run the program:

1. Download and install Java 16 from [Oracle's official website](https://www.oracle.com/java/technologies/javase-jdk16-downloads.html).
2. Download the latest version of Chrome Driver from [Google Chrome Labs](https://googlechromelabs.github.io/chrome-for-testing/#stable).
3. Specify the path to the downloaded Chrome Driver in the `utils.kt` file.

Once these steps are completed, launch the program. A browser window will open, and all data sent and received by WhatsApp will be displayed in the console.