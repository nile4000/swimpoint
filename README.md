# 🏊‍♂️Swimpoint
Watch App for Course Correction in Open Water Swimming

## Overview

The goal of this project is to develop a smartwatch app that assists open water swimmers in maintaining their course. The app continuously tracks swimming movements using sensors (accelerometer, gyroscope, magnetometer) and calculates the swimmer's direction. When a significant and consistent deviation from the planned course is detected over several strokes, an unobtrusive visual indicator is activated in the periphery to prompt the swimmer to correct their course.

## Goals

### 📱Must
- Develop a smartwatch app that provides real-time visual feedback to open water swimmers when significant course deviations occur.

### 🌍Should
- Integrate and fuse sensor signals (accelerometer & gyroscope add. magnetometer) to accurately detect swimming direction and movement.
- Develop an algorithm that detects swimming strokes and continuously evaluates course deviation.
- Design a minimalist indicator that appears in the peripheral view to unobtrusively support the swimmer.
- Ensure the robustness of the app, with potential offline functionality.

### Out of Scope
- Development of additional hardware or external devices.
- Integration of complex navigation systems using GPS in areas with poor signal reception (given GPS limitations underwater).
- Comprehensive analysis of performance data (e.g., training statistics) – the focus is solely on course correction.

## Architecture Overview

The app is divided into several clearly defined modules, each responsible for a specific function. The objective is to process sensor data in real time, detect swimming strokes and course deviations, and provide appropriate feedback in the periphery.

### Modules

#### 1. Sensor Manager & Data Acquisition
- **data/SensorManager:**  
  Captures data from built-in sensors (accelerometer, gyroscope, optionally magnetometer).
- **Implementation:**  
  Uses Android Sensor APIs.
- **Tasks:**
  - Regular polling and buffering of sensor data.
  - Ensure water resistance and energy optimization (e.g., managing the sampling rate).

#### 2. Sensor Fusion & Preprocessing
- **Function:**  
  Combines and filters raw data to obtain a consistent representation of the movement.
- **Implementation:**
  - Use of filters (e.g., Kalman filter) to reduce noise.
  - Calculation of current orientation and movement vectors.
- **Task:**  
  Provide stable data for the stroke detection and course correction algorithm.

#### 3. Stroke & Course Deviation Algorithm
- **Function:**  
  Analyzes the preprocessed data to:
  - Recognize recurring swimming stroke patterns.
  - Compare the current direction with a predetermined target course.
- **Implementation:**
  - Logic to detect swimming cycles (e.g., based on cyclic accelerometer data).
  - Calculate the deviation (in degrees) and set threshold values for feedback.
- **Task:**  
  Determine when and how the visual indicator should be activated.

#### 4. UI & Feedback Module
- **/ui:**  
  Displays feedback on the smartwatch.
- **Implementation:**
  - Uses Android UI frameworks (e.g., traditional Android Views) for Wear OS.
  - Features a minimalist design that shows the indicator in the peripheral view (potentially with animations or color changes).
  - Optionally integrates haptic feedback (vibrations) using the Wearable API.
- **Task:**  
  Present information in an unobtrusive and intuitive way so that the swimmer is not distracted.

## Data Flow

1. **Sensor Data:**  
   Continuously polled by the Sensor Manager and forwarded to the Sensor Fusion module.
2. **Preprocessing:**  
   The Sensor Fusion module filters the data and calculates the current orientation and movement vectors.
3. **Algorithm:**  
   The Stroke & Course Deviation Algorithm processes the filtered data, detects swimming cycles, and compares the current course with the target.
4. **Feedback:**  
   When a course deviation is detected, a corresponding feedback command is sent to the UI module.
5. **Display:**  
   The UI module shows a visual indicator (and optionally a haptic signal) in the peripheral view to prompt the swimmer to correct their course.

## Testing Phase 1

### Physical Test Environment
- **Environment:**  
  A 25-meter pool is sufficient for initial field test recording.

- **Test Procedure:**
  - The tester swims one length (25 meters) in the pool.
  - On average, 8–12 swimming strokes are performed per length.
  - **Objective:**  
    The sensor/algorithm should trigger once per length (25 m) and activate the visual (and optionally haptic) indicator correctly.

### Test Criteria
- **Functionality:**
  - The sensor reliably detects 7–12 swimming strokes per length.
  - The algorithm correctly identifies the swimming cycle and compares the current direction with the target course.
  - The indicator is activated only once per length when significant course deviations occur.
- **Robustness:**
  - The app operates reliably in a wet environment.
  - Sensor data is stable enough to prevent false triggers.
