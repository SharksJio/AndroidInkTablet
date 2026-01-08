# AndroidInkTablet
A production-quality note-taking and writing application using AndroidX Ink Library for Android, designed specifically for tablets in portrait mode with MVVM architecture.

## Features

### 🎨 Drawing Tools
- **Pen Tool**: Standard digital ink pen with pressure sensitivity and adjustable sizes
- **Pencil Tool**: Softer drawing tool with reduced opacity for sketching
- **Marker Tool**: Wide brush tool with transparency for marking
- **Highlighter Tool**: Extra-wide transparent tool for highlighting text
- **Eraser Tool**: With two modes - Stroke Eraser (entire stroke) and Part Eraser (partial)
- **Lasso Selection Tool**: Select and manipulate drawn strokes

### 🛠️ Advanced Toolbar Features
- **Floating Toolbar**: Draggable toolbar that can be moved anywhere on screen
- **Minimize/Maximize**: Toolbar can be collapsed to save screen space
- **Undo/Redo**: Quick access buttons in floating toolbar
- **Color Palette**: 8 primary colors for quick selection
- **Long-Press Actions**:
  - Pen: Select from 4 different sizes
  - Eraser: Switch between stroke and part eraser modes

### 📝 Content Creation
- **Text Insertion**: Add text annotations to your drawings
- **Shape Tools**: Create perfect circles, rectangles, and triangles
- **Image Insertion**: Add images from gallery to your drawings
- **Background Patterns**: Choose from Plain, Grid, Dots, or Lines

### 💾 File Management
- **Save/Load**: Save drawings internally and load them later
- **Export Options**:
  - PNG format (lossless)
  - JPEG format (compressed)
  - PDF format (document)
- **Auto-naming**: Files automatically named with timestamps

### 🤖 AI Integration (Ready)
- **Text Recognition**: Convert handwriting to digital text (ML Kit integration ready)
- **AI Agent Support**: Architecture prepared for AI agent integration
- **Shape Detection**: Future support for automatic shape recognition

### 🎯 UI/UX Features
- **Material Theme 3**: Latest Material Design with dynamic theming
- **Portrait Mode**: Locked to portrait orientation for optimal tablet use
- **Pressure Sensitivity**: Full stylus pressure support for variable line thickness
- **Edge-to-Edge Display**: Modern Android design patterns
- **Responsive Layout**: Optimized specifically for tablets
- **Action Bar**: 
  - Left side: Folder (open), New file, Save icons
  - Right side: Overflow menu with Export and Background options
  - Close button to exit

## Technical Specifications

### Architecture
- **Pattern**: MVVM (Model-View-ViewModel)
- **Components**:
  - ViewModels for state management
  - Repository pattern for file operations
  - LiveData for reactive UI updates
  - Kotlin Coroutines for async operations

### Target Specifications
- **Target API**: Android 34 (Android 14)
- **Minimum API**: Android 26 (Android 8.0)
- **Build Tools**: 36.0.0
- **Language**: Kotlin
- **UI Framework**: Traditional Android Views (no Jetpack Compose)

### Dependencies
- AndroidX Ink Library (beta01) - Digital ink handling
- Material Components 3 - Modern UI components
- AndroidX Lifecycle - MVVM components
- Kotlin Coroutines - Async operations
- ML Kit Digital Ink Recognition - Text recognition
- iText7 - PDF generation

## Building the Project

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK 34
- Kotlin 1.9.20+
- Gradle 8.0+

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
   - Connect an Android device or start an emulator (tablet recommended)
   - Click "Run" in Android Studio or use:
   ```bash
   ./gradlew installDebug
   ```

### Project Structure

```
app/src/main/
├── java/com/sharks/androidinktablet/
│   ├── MainActivity.kt                 # Main activity with MVVM architecture
│   ├── InkTabletApplication.kt        # Application class
│   ├── viewmodel/
│   │   └── DrawingViewModel.kt        # ViewModel for state management
│   ├── model/
│   │   ├── BackgroundType.kt          # Background pattern types
│   │   ├── DrawingFile.kt             # Drawing file data model
│   │   ├── ShapeType.kt               # Shape types enum
│   │   └── EraserMode.kt              # Eraser mode types
│   ├── repository/
│   │   └── FileRepository.kt          # File operations repository
│   ├── drawing/
│   │   ├── DrawingView.kt             # Custom drawing canvas
│   │   ├── Tool.kt                    # Drawing tool definitions
│   │   └── Stroke.kt                  # Stroke data structures
│   └── ui/
│       ├── ColorPickerDialog.kt       # Color selection dialog
│       └── DraggableToolbarContainer.kt # Draggable toolbar wrapper
├── res/
│   ├── layout/
│   │   ├── activity_main.xml          # Main layout
│   │   └── floating_toolbar.xml       # Floating toolbar layout
│   ├── drawable/                      # Vector icons and patterns
│   ├── values/                        # Colors, strings, themes
│   ├── menu/                          # Menu definitions
│   └── xml/                           # FileProvider paths
└── AndroidManifest.xml               # App permissions and configuration
```

## Usage Guide

### Getting Started
1. Launch the app - it opens in portrait mode optimized for tablets
2. The floating toolbar appears in the center - drag it anywhere you want
3. Select a drawing tool from the toolbar
4. Start drawing on the canvas

### Using the Floating Toolbar
- **Drag**: Touch and hold the toolbar, then drag to reposition
- **Minimize**: Tap the toggle button (left-most) to minimize toolbar
- **Tool Selection**: Tap any tool button to activate it
- **Colors**: Tap any color button to change drawing color
- **Undo/Redo**: Quick access buttons for undo and redo

### Tool Usage
- **Pen**: Tap to select, long-press for size options (Small/Medium/Large/XL)
- **Highlighter**: Wide transparent strokes for highlighting
- **Eraser**: Tap to select, long-press to choose Stroke or Part eraser mode
- **Text**: Tap to insert text, enter text in dialog
- **Shapes**: Tap to create shapes (circle, rectangle, triangle)
- **Lasso**: Select multiple strokes for manipulation

### File Operations (Top Action Bar)
- **Folder Icon**: Open saved drawings
- **New Icon**: Create new drawing (prompts to save current)
- **Save Icon**: Save current drawing
- **Menu (⋮)**:
  - Export as PNG
  - Export as JPEG  
  - Export as PDF
  - Change Background (Plain/Grid/Dots/Lines)
  - Convert to Text
  - Clear Page
- **Close (✕)**: Exit application

### Background Patterns
1. Tap the overflow menu (⋮) in action bar
2. Select "Change Background"
3. Choose from:
   - **Plain**: White background
   - **Grid**: Square grid pattern
   - **Dots**: Dotted pattern for alignment
   - **Lines**: Horizontal ruled lines

### Text Recognition
1. Draw some handwriting on canvas
2. Tap overflow menu → "Convert to Text"
3. View recognized text in dialog (requires ML Kit model)

### Exporting Drawings
1. Tap overflow menu (⋮)
2. Select export format:
   - **PNG**: For lossless image quality
   - **JPEG**: For smaller file size
   - **PDF**: For documents
3. Files saved to Downloads folder

## Permissions

The app requests the following permissions:
- `READ_MEDIA_IMAGES` (Android 13+): Access gallery images for insertion
- `READ_EXTERNAL_STORAGE` (Android 12 and below): Access stored files
- `WRITE_EXTERNAL_STORAGE` (Android 9 and below): Save exported files
- `INTERNET`: Network access for future AI features

## MVVM Architecture

### ViewModel (DrawingViewModel)
Manages UI state including:
- Current tool selection
- Brush size and pressure sensitivity
- Color selection
- Background type
- Undo/redo state
- Current file

### Repository (FileRepository)
Handles file operations:
- Save drawings internally
- Load saved drawings
- Export to PNG/JPEG/PDF
- List all saved drawings
- Delete drawings

### View (MainActivity + DrawingView)
- MainActivity: Orchestrates UI, observes ViewModel
- DrawingView: Custom canvas for drawing with AndroidX Ink

## Extending with AI Agent

The application is designed to be extended with AI capabilities:

1. **Text Recognition**: Integrate ML Kit Digital Ink Recognition model
2. **Shape Detection**: Add AI-powered shape recognition
3. **Smart Suggestions**: Implement drawing assistance
4. **Auto-Correction**: Add stroke beautification
5. **Handwriting Improvement**: AI-powered handwriting enhancement

Architecture is prepared with:
- Separated concerns (MVVM)
- Repository pattern for data operations
- AndroidX Ink library integration
- Placeholder methods for AI features

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## License

This project is open source and available under the [MIT License](LICENSE).

## Acknowledgments

- AndroidX Ink Library for digital ink handling
- Material Design team for UI components
- Google ML Kit for text recognition capabilities
- Android development community for best practices

## Screenshots

### Main Interface
- Floating draggable toolbar with all tools
- Clean canvas with background pattern support
- Action bar with file operations

### Tools in Action
- Pen with pressure sensitivity
- Highlighter for emphasis
- Shape creation tools
- Text insertion capability

### File Management
- Save and load drawings
- Export to multiple formats
- File browser for saved drawings

### Background Patterns
- Plain white background
- Grid pattern for alignment
- Dotted pattern for precision
- Ruled lines for writing

---

**Note**: This is a production-quality application designed specifically for tablets in portrait mode. For best experience, use a tablet device with stylus support.

