# GeoMotion - Repository

## Overview

GeoMotion is an innovative Android application designed to capture and associate emotions with geographic locations using audio recordings. This project leverages a Neural Network to extract emotions directly from audio, allowing users to explore the emotional landscape of different areas. The application integrates Firebase for user authentication, Google Maps API for location services, and Google Cloud for audio storage. 

## Key Features

- **Emotion Recognition from Audio**: Uses a Neural Network to identify emotions directly from audio recordings, bypassing traditional Speech-to-Text processes.
- **User Authentication**: Managed via Firebase, allowing seamless sign-in with Google accounts.
- **Location Mapping**: Utilizes Google Maps API to link audio recordings with geographic locations.
- **Efficient Storage**: Audio recordings are stored in Google Cloud Storage, with URLs saved in Firestore for efficient data retrieval.
- **Energy Efficiency**: Optimized power consumption to ensure sustainability for mobile users.
- **Potential Applications**: Social media sharing, sociological and psychological research, tourism insights.

## Architecture

GeoMotion's architecture integrates several key components:

1. **Firebase**: Manages user authentication and stores user session information.
2. **Google Maps API**: Displays maps and current user location.
3. **Firestore**: Stores metadata and URLs for audio recordings.
4. **Google Cloud Storage (GCS)**: Stores the actual audio files.
5. **TFLite Model**: Processes audio data to recognize emotions, achieving approximately 73% accuracy.

## Installation

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/your-username/GeoMotion.git
   ```
2. **Open the Project in Android Studio**:
   - Ensure you have the latest version of Android Studio installed.
   - Open the project directory in Android Studio.
3. **Configure Firebase**:
   - Create a Firebase project and add the Android app.
   - Download the `google-services.json` file and place it in the `app` directory.
4. **Build and Run the Application**:
   - Sync the project with Gradle files.
   - Build and run the application on an Android device or emulator.

## Usage

1. **Sign In**:
   - Open the app and sign in using your Google account.
2. **Record Audio**:
   - Allow microphone and location access.
   - Record audio and save it with the associated location.
3. **Explore Emotions on the Map**:
   - View different areas on the map and explore the predominant emotions associated with them.
   - Play back audio recordings to hear the sounds linked to specific locations.

## Future Developments

- **Model Customization**: Enhance performance by allowing user-specific emotion models.
- **Multimodal Classifier**: Incorporate additional data from smart devices (e.g., heart rate) for improved emotion recognition.
- **Privacy Enhancements**: Implement follower/following features to control who can access recordings.
- **Tourism Features**: Enable searches for specific emotions in different areas, aiding tourists in exploring vibrant or safer places.

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Contact

For any questions or suggestions, please contact:

- Davide Bruni: d.bruni6@studenti.unipi.it
- Daniel Deiana: d.deiana1@studenti.unipi.it
- Marco Galante: m.galante1@studenti.unipi.it
- Lorenzo Guidotti: l.guidotti6@email.com

---

Thank you for using GeoMotion! We hope our application helps you explore the emotional landscape of your surroundings in a new and insightful way.
