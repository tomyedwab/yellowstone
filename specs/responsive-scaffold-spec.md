# Responsive Scaffold Specification

## Overview
Application-wide layout container that provides responsive navigation and content organization. Adapts between mobile (vertical) and desktop/tablet (horizontal) layouts.

## Layout Modes

### Vertical Layout (Mobile)
- **Navigation**: Bottom navigation bar with 4 tabs
- **Content**: Single full-screen content panel
- **Active Content**: Shows last child (highest index) from children array

### Horizontal Layout (Desktop/Tablet)
- **Navigation**: Left sidebar navigation rail
- **Content**: Multi-panel layout with vertical dividers
- **Panels**: Up to 3 content panels with fixed/flexible widths

## Navigation Structure

### Navigation Items (Both Layouts)
1. **Lists Tab/Rail Item**
   - Icon: List icon with version info gesture
   - Label: "Lists"
   - Route: `/` (home)
   - Long-press shows version dialog

2. **Labels Tab/Rail Item**
   - Icon: Label icon
   - Label: "Labels"
   - Route: `/labels`

3. **Templates Tab/Rail Item**
   - Icon: Copy/template icon
   - Label: "Templates"
   - Route: `/templates`

4. **Archived Tab/Rail Item**
   - Icon: Archive icon
   - Label: "Archived"
   - Route: `/archived`

### Version Information Dialog
- **Trigger**: Long press on Lists icon
- **Title**: "Yellowstone"
- **Content**: Client version + Server version information
- **Action**: Single "OK" button to close

## Content Panel Layout (Horizontal Mode)

### Panel Configuration
- **Single Child**: Expanded to fill available space
- **Two Children**:
  - Panel 1: Fixed 300px width
  - Panel 2: Expanded to remaining space
- **Three Children**:
  - Panel 1: Fixed 300px width
  - Panel 2: Fixed 350px width
  - Panel 3: Expanded to remaining space

### Panel Separators
- **Vertical Dividers**: Between panels
- **Color**: #182631 (dark blue-gray)
- **Thickness**: 2px
- **Width**: 1px

## API Information Access
- **Server Version**: Retrieved from data service
- **Client Version**: Retrieved from package information
- **Display Format**: "Client version X.X.X\nServer version Y.Y.Y"

## Responsive Breakpoints
- **Layout Detection**: Uses responsive service to determine layout type
- **Automatic Switching**: Adapts based on screen size and device capabilities
- **Navigation Adaptation**: Bottom bar ↔ sidebar rail seamlessly

## Navigation Behavior
- **Selected Index**: Highlights current active section
- **Color Scheme**: Indicator color #7faad0 (light blue)
- **Route Handling**: Direct navigation using context.go()

## Integration Points
- **Data Service**: Receives RestDataService for server version info
- **Responsive Service**: Receives ResponsiveService for layout decisions
- **Children Components**: Receives array of content components to display

## Visual Theme
- **Background**: Inherits app theme colors
- **Navigation Colors**:
  - Indicator: #7faad0 (light blue)
  - Icons/Text: Default theme colors
- **Consistent Styling**: Matches overall app design system

## Accessibility
- **Navigation Labels**: All navigation items have descriptive labels
- **Focus Management**: Proper focus handling between navigation modes
- **Screen Reader Support**: Semantic navigation structure

## State Management
- **Selected Index**: Passed from parent routing system
- **Children Management**: Dynamic panel display based on children array length
- **Layout Responsiveness**: Automatic adaptation without state loss