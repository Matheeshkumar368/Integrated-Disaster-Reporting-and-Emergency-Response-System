# Loading Screen Component

A visually stunning, modern loading overlay for web applications with smooth animations and easy API.

## Features

- Smooth animated logo with pulsing effect
- Multiple progress indicators (spinner, bar, dots)
- Animated particle background with gradient shifts
- Hardware-accelerated CSS animations (60fps)
- Semi-transparent full-viewport overlay
- Light and dark theme support (auto-detects system preference)
- Responsive design for desktop and mobile
- Clean API with callbacks

## Quick Start

### 1. Include the files

```html
<link rel="stylesheet" href="loading-screen.css">
<script src="loading-screen.js"></script>
```

### 2. Use the API

```javascript
// Show loading with custom message
LoadingScreen.show('Signing you in...');

// Show with options
LoadingScreen.show('Loading...', {
    theme: 'dark',           // 'light', 'dark', 'auto'
    progressType: 'spinner', // 'spinner', 'bar', 'dots'
    backdropBlur: true,      // enable backdrop blur effect
    showProgress: true      // show progress indicator
});

// Hide with callback
LoadingScreen.hide(() => {
    console.log('Animation complete!');
});

// Change theme
LoadingScreen.setTheme('light');

// Set custom logo (HTML string)
LoadingScreen.setLogo('<svg>...</svg>');
```

## API Reference

| Method | Parameters | Description |
|--------|------------|-------------|
| `show(message, options)` | message: string, options?: object | Show loading screen |
| `hide(callback)` | callback?: function | Hide loading screen |
| `setTheme(theme)` | theme: 'light' \| 'dark' \| 'auto' | Set theme |
| `setLogo(logoHTML)` | logoHTML: string | Set custom logo |
| `setOptions(options)` | options: object | Set default options |

## Options

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `theme` | string | 'auto' | Color scheme |
| `duration` | number | 300 | Fade duration in ms |
| `progressType` | string | 'spinner' | Progress indicator style |
| `backdropBlur` | boolean | true | Enable backdrop blur |
| `showProgress` | boolean | true | Show progress element |
| `logo` | string | null | Custom logo HTML |

## Examples

### Login Authentication

```javascript
async function login(email, password) {
    LoadingScreen.show('Signing you in...');
    
    try {
        await api.auth.login(email, password);
        LoadingScreen.hide(() => {
            window.location.href = '/dashboard';
        });
    } catch (error) {
        LoadingScreen.hide();
        showError(error.message);
    }
}
```

### Page Transitions

```javascript
function navigateTo(page) {
    LoadingScreen.show('Loading your dashboard...');
    
    router.navigate(page).then(() => {
        LoadingScreen.hide();
    });
}
```

### With Custom Logo

```javascript
LoadingScreen.setLogo(`
    <svg viewBox="0 0 100 100">
        <circle cx="50" cy="50" r="45" fill="#6366f1"/>
        <text x="50" y="55" text-anchor="middle" fill="white" font-size="24">A</text>
    </svg>
`);
LoadingScreen.show('Authenticating...');
```

## Browser Support

- Chrome 60+
- Firefox 55+
- Safari 11+
- Edge 79+

Supports reduced motion preferences (disables animations).