# AndroidInkTablet
A complete note-taking application using Google Ink API for Android 14+ with modern Material Theme 3 design.

## Features

### Drawing Tools
- **Pen Tool**: Standard digital ink pen with pressure sensitivity
- **Pencil Tool**: Softer drawing tool with reduced opacity for sketching
- **Marker Tool**: Wide brush tool with transparency for highlighting
- **Eraser Tool**: Point eraser for precise corrections
- **Lasso Selection Tool**: Select and manipulate drawn strokes

### Advanced Features
- **Pressure Sensitivity**: Full stylus pressure support for variable line thickness
- **Color Picker**: Comprehensive color palette with 18 predefined colors
- **Brush Size Control**: Adjustable brush size from 1-50px
- **Undo/Redo**: Full command history with 50-step undo/redo
- **Image Insertion**: Add images from gallery to your drawings
- **AI-Powered Recognition**: 
  - Text recognition from handwritten notes
  - Shape detection and optimization

### Modern UI/UX
- **Material Theme 3**: Latest Material Design with dynamic theming
- **Dark/Light Mode**: Automatic theme switching support
- **Edge-to-Edge Display**: Modern Android 14 design patterns
- **Responsive Layout**: Optimized for tablets and phones
- **Floating Action Buttons**: Quick access to undo/redo functions

## Technical Specifications

- **Target API**: Android 34 (Android 14)
- **Minimum API**: Android 26 (Android 8.0)
- **Architecture**: MVVM with custom drawing engine
- **Dependencies**:
  - Google ML Kit Digital Ink Recognition
  - Material Components 3
  - AndroidX libraries
  - Kotlin Coroutines

## Building the Project

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 34
- Kotlin 1.9.10+

### Setup Instructions

1. **Clone the repository**
   ```bash
   git clone https://github.com/SharksJio/AndroidInkTablet.git
   cd AndroidInkTablet
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned repository
   - Wait for Gradle sync to complete

3. **Build the project**
   ```bash
   ./gradlew assembleDebug
   ```

4. **Run on device/emulator**
   - Connect an Android device or start an emulator
   - Click "Run" in Android Studio or use:
   ```bash
   ./gradlew installDebug
   ```

### Project Structure

```
app/src/main/
├── java/com/sharks/androidinktablet/
│   ├── MainActivity.kt                 # Main activity with drawing interface
│   ├── InkTabletApplication.kt        # Application class
│   ├── drawing/
│   │   ├── DrawingView.kt             # Custom drawing canvas
│   │   ├── Tool.kt                    # Drawing tool definitions
│   │   └── Stroke.kt                  # Stroke data structures
│   └── ui/
│       └── ColorPickerDialog.kt       # Color selection dialog
├── res/
│   ├── layout/                        # UI layouts
│   ├── drawable/                      # Vector icons and drawables
│   ├── values/                        # Colors, strings, themes
│   └── menu/                          # Menu definitions
└── AndroidManifest.xml               # App permissions and configuration
```

## Usage Guide

### Drawing
1. Select a drawing tool from the bottom toolbar
2. Adjust brush size and pressure sensitivity in the settings panel
3. Choose colors using the palette button
4. Draw on the canvas with finger or stylus

### Tool Selection
- **Pen**: Solid ink drawing
- **Pencil**: Soft sketching with transparency
- **Marker**: Wide brush for highlighting
- **Eraser**: Remove drawn strokes
- **Lasso**: Select and manipulate drawings

### AI Features
- **Text Recognition**: Menu → AI Text Recognition
- **Shape Detection**: Menu → AI Shape Detection

### File Operations
- **New**: Clear canvas for new drawing
- **Save**: Save current drawing (implementation pending)
- **Load**: Load saved drawing (implementation pending)

## Permissions

The app requests the following permissions:
- `READ_MEDIA_IMAGES` (Android 13+): Access gallery images
- `READ_EXTERNAL_STORAGE` (Android 12 and below): Access stored files
- `INTERNET`: Download ML models for text recognition

## Contributing

1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License

This project is open source and available under the [MIT License](LICENSE).

## Acknowledgments

- Google ML Kit for digital ink recognition
- Material Design team for UI components
- Android development community for best practices
