# Yellowstone - Theme Specification

## Overview

The Yellowstone application uses a consistent dark theme throughout the interface following Material Design 3 principles. This specification documents all theme-related elements including colors, typography, component styling, and layout patterns for implementation across all platforms (web, mobile, desktop).

## Theme Configuration

### Base Theme Settings
- **Design System**: Material Design 3
- **Theme Mode**: Dark theme
- **Color Scheme**: Custom dark color scheme with specific brand colors

## Color Palette

### Primary Colors
- **Primary**: `#f6fbff` (Light blue-white, used for primary text and icons)
- **Surface/Background**: `#111e2a` (Dark blue-grey, main background color)

### Secondary Colors
- **Background**: `#111e2a` (Same as surface)
- **Border Color**: `#182631` (Darker blue-grey for borders and separators)
- **Selection Highlight**: `#314150` - Used for selected items
- **Text Colors**:
  - Primary text: `#f6fbff`
  - Secondary text: `#ffffff` at 70% opacity
  - Hint text: `#ffffff` at various opacities

### Component-Specific Colors
- **Chip Background**: `#000000` at 26% opacity
- **Chip Border**: `#ffffff` at 30% opacity
- **Dividers**: Transparent (invisible dividers)

## Typography

### Font Families
The application uses two primary typefaces:

1. **Roboto**
   - Used for: Body text, general content, task titles
   - Applied to: Body text variants, general UI text

2. **Archivo**
   - Used for: App bar titles, headers
   - Weight: Medium (500)
   - Size: 28px

### Text Styles

#### Header/Title Styles
- **App Bar Title**: Archivo, 28px, Medium weight, color `#f6fbff`
- **Page Headers**: Roboto, various sizes, color `#f6fbff`

#### Body Text Styles
- **Primary Body Text**: Roboto, color `#f6fbff`
- **Secondary Body Text**: Roboto, color `#ffffff` at 70% opacity

#### Task-Specific Text Styles
- **Task Title**: 16px, Regular weight (400), color `#ffffff`
- **Task Subtitle**: 14px, color `#ffffff` at 70% opacity
- **Task Comments**: 14px, color `#ffffff` at 70% opacity, italic style
- **Chip Labels**: 12px, color `#ffffff` at 70% opacity

## Component Styling

### App Bar Styling
- **Background Color**: `#111e2a`
- **Surface Tint**: `#111e2a`
- **Elevation**: 0 (flat design)
- **Icon Color**: `#f6fbff`
- **Title Style**: Archivo font, as specified above

### List Items
- **Icon Color**: `#f6fbff`
- **Text Color**: `#f6fbff`
- **Padding**: 16px horizontal, 8px vertical

### Icons
- **Default Color**: `#f6fbff`
- **Interactive States**: Maintain same color

### Card Styling
- **Elevation**: 0 (flat design)
- **Border Radius**: 0px for new task cards, 8px for regular cards
- **Background**: Transparent or matches surface color

## Layout Patterns

### Container Decorations

#### Task Card Container
- **Background Color**: `#314150` when highlighted, transparent otherwise
- **Border Radius**: 8px
- **Border**: Bottom border only, `#182631` color, 1.5px width

#### Label List Item Container
- **Background Color**: `#314150` when selected, transparent otherwise
- **Border Radius**: 8px
- **Border**: Bottom border only, `#182631` color, 1.5px width

### Spacing and Layout
- **Task Cards**:
  - Margin: 16px left, 4px right, 4px top/bottom
- **New Task Card**:
  - Margin: 12px all sides
  - Content padding: 52px left, 4px right/top/bottom
- **General Card**:
  - Margin: 8px all sides
- **List Items**:
  - Margin: 4px horizontal, 4px vertical

## Interactive Elements

### Checkboxes and Selection
- **Checkbox Shape**: Rounded rectangle with 4px border radius
- **Task Completion Circle**:
  - Size: 36px width × 24px height
  - Border: 2px solid white
  - Shape: Circle
  - Fill: White check icon when completed

### Chips/Tags
- **Background**: `#000000` at 26% opacity
- **Border**: 0.5px solid `#ffffff` at 30% opacity
- **Text**: 12px, `#ffffff` at 70% opacity
- **Density**: Compact spacing

### Input Fields
- **Login Forms**: Outlined border style
- **Task Creation**: No visible border
- **Dialogs**: Standard outlined border style

## State-Based Styling

### Selection States
- **Highlighted Items**: Background color `#314150`
- **Selected Items**: Same highlight color as above
- **Completed Tasks**: Strike-through text decoration on title

### Loading States
- **Loading Indicator**: Circular progress indicator with theme colors
- **Loading Text**: Standard body text styling

## Dark Theme Considerations

The entire application is designed with a dark-first approach:
- All backgrounds use the dark surface color `#111e2a`
- Text consistently uses light colors for contrast
- Borders and separators use subtle dark variants
- Interactive elements maintain sufficient contrast ratios
- No light mode implementation present

## Implementation Notes

### Font Implementation
- **Roboto**: System font fallback acceptable if Google Fonts unavailable
- **Archivo**: Fallback to system sans-serif font with medium weight
- **Loading**: Fonts should be loaded with appropriate fallbacks

### Color Implementation
1. **Consistency**: All theme values should be defined as constants
2. **Performance**: Colors should be cached/pre-defined for optimal performance
3. **Accessibility**: High contrast maintained between text and background (WCAG compliant)
4. **Responsiveness**: Theme adapts to different screen sizes and orientations
5. **Material Design**: Follows Material Design 3 guidelines for dark themes

### Platform Considerations
- **Web**: CSS custom properties recommended for easy theme switching
- **Mobile**: Native theme APIs should be used where available
- **Desktop**: System theme integration where appropriate
