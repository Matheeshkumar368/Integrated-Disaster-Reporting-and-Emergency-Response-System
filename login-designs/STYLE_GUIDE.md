# Login Page Design System - Style Guide

## Overview
This style guide defines the visual language for the login page variants. All designs share a common design token system but differ in aesthetic approach.

---

## Color Palettes

### Primary Colors (Role-Based)
| Role | Primary | Primary Light | Use Case |
|------|---------|---------------|----------|
| Admin | `#dc2626` | `#ef4444` | Danger/authority actions |
| Responder | `#3b82f6` | `#60a5fa` | Primary actions, links |
| Citizen | `#10b981` | `#34d399` | Success, confirmation |

### Neutral Colors
| Name | Value | Usage |
|------|-------|-------|
| Background Dark | `#0a0f1a` | Page background |
| Background Darker | `#060810` | Deep background |
| Surface | `rgba(15, 23, 42, 0.95)` | Card backgrounds |
| Surface Glass | `rgba(30, 41, 59, 0.7)` | Glass effect |
| Border | `rgba(148, 163, 184, 0.15)` | Subtle borders |
| Border Light | `rgba(148, 163, 184, 0.25)` | Elevated borders |

### Text Colors
| Level | Value | Contrast | Usage |
|-------|-------|----------|-------|
| Primary | `#f1f5f9` | 14.5:1 | Headings, important text |
| Secondary | `#94a3b8` | 6.1:1 | Labels, descriptions |
| Muted | `#64748b` | 4.5:1 | Placeholders, hints |

### State Colors
| State | Color | Usage |
|-------|-------|-------|
| Error | `#f87171` | Validation errors |
| Success | `#34d399` | Success messages |
| Warning | `#fbbf24` | Warnings |

---

## Typography

### Font Stack
```css
font-family: 'Segoe UI', system-ui, -apple-system, BlinkMacSystemFont, sans-serif;
```

### Scale
| Size | Value | Line Height | Usage |
|------|-------|-------------|-------|
| xs | 0.75rem | 1 | Small labels |
| sm | 0.875rem | 1.5 | Secondary text |
| base | 1rem | 1.5 | Body text |
| lg | 1.125rem | 1.5 | Emphasis |
| xl | 1.25rem | 1.25 | Section titles |
| 2xl | 1.5rem | 1.25 | Page titles |
| 3xl | 1.875rem | 1.25 | Hero text |

### Weights
- Normal: 400
- Medium: 500
- Semibold: 600
- Bold: 700

---

## Button Styles

### Primary Button
```css
height: 54px;
border-radius: 12px;
font-size: 1.05rem;
font-weight: 600;
background: linear-gradient(135deg, var(--primary), var(--primary-light));
transition: transform 150ms ease, box-shadow 300ms ease;
```

### Hover State
```css
transform: translateY(-2px);
box-shadow: 0 10px 30px rgba(59, 130, 246, 0.4);
```

### Active State
```css
transform: translateY(0);
box-shadow: none;
```

### Disabled State
```css
opacity: 0.6;
cursor: not-allowed;
transform: none;
```

### Role-Specific Variants
- **Admin**: Red gradient (`#dc2626` → `#ef4444`)
- **Responder**: Blue gradient (`#3b82f6` → `#60a5fa`)
- **Citizen**: Green gradient (`#10b981` → `#34d399`)

---

## Input States

### Default
```css
height: 52px;
background: rgba(0, 0, 0, 0.3);
border: 1px solid rgba(148, 163, 184, 0.15);
border-radius: 12px;
color: #f1f5f9;
```

### Focus
```css
border-color: #60a5fa;
box-shadow: 0 0 0 3px rgba(96, 165, 250, 0.2), 0 0 20px rgba(96, 165, 250, 0.1);
```

### Error
```css
border-color: #f87171;
box-shadow: 0 0 0 3px rgba(248, 113, 113, 0.2);
```

### Disabled
```css
opacity: 0.5;
cursor: not-allowed;
```

---

## Spacing Scale

| Token | Value | Usage |
|-------|-------|-------|
| space-1 | 0.25rem | Tight spacing |
| space-2 | 0.5rem | Icon gaps |
| space-3 | 0.75rem | Related elements |
| space-4 | 1rem | Default spacing |
| space-5 | 1.25rem | Section padding |
| space-6 | 1.5rem | Card padding |
| space-8 | 2rem | Large gaps |
| space-10 | 2.5rem | Section margins |
| space-12 | 3rem | Page margins |

---

## Responsive Breakpoints

| Name | Width | Layout |
|------|-------|--------|
| sm | 640px | Mobile landscape |
| md | 768px | Tablet |
| lg | 1024px | Desktop |
| xl | 1280px | Large desktop |

### Mobile-First Approach
- Base styles: mobile (no media query)
- sm: `@media (min-width: 640px)`
- md: `@media (min-width: 768px)`
- lg: `@media (min-width: 1024px)`

---

## Accessibility Checklist

### Contrast Ratios
- [ ] Text on background: minimum 4.5:1
- [ ] Large text (18px+): minimum 3:1
- [ ] UI components: minimum 3:1
- [ ] Focus indicators: minimum 3:1

### Keyboard Navigation
- [ ] All interactive elements focusable
- [ ] Logical tab order
- [ ] Visible focus states (3px outline)
- [ ] Escape closes modals/dropdowns

### Screen Readers
- [ ] Semantic HTML structure
- [ ] ARIA labels where needed
- [ ] Form labels associated with inputs
- [ ] Error messages announced

### Reduced Motion
- [ ] `prefers-reduced-motion` support
- [ ] Animations disabled gracefully
- [ ] Transitions use `transition: none`

---

## File Structure

```
login-designs/
├── design-tokens.json    # Design tokens (JSON)
├── STYLE_GUIDE.md         # This file
├── VARIANT_1_NORTHERN/    # Variant 1: Northern Lights
│   ├── index.html
│   ├── styles.css
│   └── README.md
├── VARIANT_2_CRISIS/      # Variant 2: Crisis Command
│   ├── index.html
│   ├── styles.css
│   └── README.md
└── VARIANT_3_TERRA/       # Variant 3: Terra Nova
    ├── index.html
    ├── styles.css
    └── README.md
```

---

## Integration Guide

### Vanilla HTML
1. Copy variant folder to project
2. Update paths in `<link>` and `<script>` tags
3. Test form submission

### React (Example Component)
```jsx
import './styles.css';

function LoginPage() {
  return (
    <div className="login-wrapper">
      {/* Copy HTML structure */}
    </div>
  );
}
```

### Vue (Example Component)
```vue
<template>
  <div class="login-wrapper">
    <!-- Copy HTML structure -->
  </div>
</template>

<style scoped>
@import './styles.css';
</style>
```

### Angular (Example Component)
```typescript
@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {}
```

---

## Usage Recommendations

| Variant | Best For | Avoid When |
|---------|----------|------------|
| Northern Lights | Modern apps, dashboards | Emergency services |
| Crisis Command | Emergency/alert systems | Consumer apps |
| Terra Nova | General enterprise | Very formal government |

---

## Next Steps

Before finalizing, please share:

1. **Brand constraints**: Any existing brand colors or guidelines?
2. **Logo assets**: Want to add a logo to the login page?
3. **Framework**: Using React, Vue, Angular, or vanilla?
4. **Accessibility requirements**: WCAG level, specific needs?
5. **Localization**: Need i18n support?
6. **Social login**: Which providers (Google, Facebook, etc.)?