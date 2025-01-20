# Warehouse Monitoring Demo 

### Introduction
This demo features two Netty servers within the warehouse service that receive UDP data from sensors 
and forward it to a Kafka cluster. The monitoring service reads the data from Kafka and prints alert 
information to the console if any thresholds are exceeded.

### How to Run the Demo

#### Prerequisites
Ensure that **Docker** and **Netcat** are installed on your local machine.

#### Steps
1. Open a terminal and navigate to the project's root folder.
2. Run the following command to start the necessary services:
   ```
   docker-compose up
   ```
   This will launch **Kafka, the warehouse service,** and the **central monitoring service**.
3. Open another terminal and execute:
   ```
   nc -u 127.0.0.1 3344
   ```
   This connects to the **temperature UDP server**.
4. Open yet another terminal and execute:
   ```
   nc -u 127.0.0.1 3355
   ```
   This connects to the **humidity UDP server**.
5. Once connected, enter sensor data in the following format:
   ```
   sensor_id=t1; value=99
   ```
   Replace the values as needed to simulate sensor input.
6. If the entered value exceeds the predefined threshold, logs will be displayed in the first terminal.

   Alternatively, to view logs specifically for the **central monitoring service**, run:
   ```
   docker-compose logs -f monitoring-service
   ```

---
