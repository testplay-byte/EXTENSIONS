# Dark Neon Design System

> A comprehensive design language document for building dark-themed, neon-accented web applications with glass-morphism effects, floating UI patterns, and Framer Motion animations. Designed for Next.js 16 + Tailwind CSS 4 + shadcn/ui.

---

## Table of Contents

1. [Philosophy](#1-philosophy)
2. [Color System](#2-color-system)
3. [Typography](#3-typography)
4. [Spacing & Layout](#4-spacing--layout)
5. [Surface & Elevation](#5-surface--elevation)
6. [Borders & Dividers](#6-borders--dividers)
7. [Buttons & Controls](#7-buttons--controls)
8. [Cards & Containers](#8-cards--containers)
9. [Icons](#9-icons)
10. [Form Inputs & Keypads](#10-form-inputs--keypads)
11. [Modals & Overlays](#11-modals--overlays)
12. [Tables & Lists](#12-tables--lists)
13. [Animations & Transitions](#13-animations--transitions)
14. [Background Effects](#14-background-effects)
15. [Scrollbars](#15-scrollbars)
16. [Navigation Patterns](#16-navigation-patterns)
17. [Responsive Breakpoints](#17-responsive-breakpoints)
18. [Component Reference](#18-component-reference)
19. [Tailwind Theme Configuration](#19-tailwind-theme-configuration)
20. [Anti-Patterns & Rules](#20-anti-patterns--rules)

---

## 1. Philosophy

| Principle | Description |
|-----------|-------------|
| **Dark-first** | Every surface starts dark. Light is added sparingly through accent colors and subtle borders. |
| **Neon accents on muted canvas** | The base palette is neutral dark gray. Accent colors (lime, sky, coral) provide all visual energy. |
| **Glass, not flat** | Surfaces use `backdrop-blur` and semi-transparent backgrounds to create depth, not solid opaque fills. |
| **Animated but not distracting** | Motion is used to communicate state changes, draw attention to live data, and create delight — never to slow the user down. |
| **Monospace for data** | All numbers, currencies, rates, and technical values use monospace fonts with tabular-nums for alignment. |
| **Mobile-first responsive** | Design for touch, then enhance for desktop. Keypads become floating popups on larger screens. |
| **Sticky footer, no float** | Footer always sticks to the viewport bottom on short pages and is pushed down naturally on long pages. |

---

## 2. Color System

### 2.1 Background Colors (Surfaces)

| Token | Hex | Usage |
|-------|-----|-------|
| `bg-base` | `#1e1e24` | Outermost background, body, root container |
| `bg-surface` | `#28282f` | Cards, content panels, stat blocks |
| `bg-sidebar` | `#242430` | Sidebar, navigation panel |
| `bg-elevated` | `#333340` | Hover states, active items, emphasis backgrounds |

**CSS variables:**
```css
--color-bg-base: #1e1e24;
--color-bg-surface: #28282f;
--color-bg-sidebar: #242430;
--color-bg-elevated: #333340;
```

**Tailwind classes:** `bg-bg-base`, `bg-bg-surface`, `bg-bg-sidebar`, `bg-bg-elevated`

### 2.2 Accent Colors (Neons)

| Token | Hex | Role | Semantic Meaning |
|-------|-----|------|-----------------|
| `accent-lime` | `#BCFF5F` | Primary accent | Success, profit, positive, primary actions, "go" |
| `accent-sky` | `#5FC9FF` | Secondary accent | Information, calculation, estimation, neutral-highlight |
| `accent-coral` | `#FF5F7E` | Danger accent | Loss, error, deletion, fee cost, "stop/danger" |

**CSS variables:**
```css
--color-accent-lime: #BCFF5F;
--color-accent-sky: #5FC9FF;
--color-accent-coral: #FF5F7E;
```

**Tailwind classes:** `text-accent-lime`, `bg-accent-lime`, `border-accent-lime`, etc.

**Usage rules:**
- Lime text on dark backgrounds for readability (high contrast: ~12:1)
- Accent buttons use accent color as background with `bg-base` as text color (e.g., lime button → dark text)
- Accent backgrounds at low opacity for subtle highlights: `bg-accent-lime/10`, `bg-accent-sky/5`
- Never use indigo or blue as primary colors
- Coral is never used as a primary action color — only for destructive/negative contexts

### 2.3 Text Colors

| Token | Hex | Opacity Equivalent | Usage |
|-------|-----|--------------------|-------|
| `foreground` | `#ffffff` | 100% | Headlines, primary values, active labels |
| `text-secondary` | `#c8c8d4` | ~78% | Body text, secondary information |
| `text-muted` | `#8888a0` | ~53% | Labels, captions, tertiary info |
| `text-dim` | `#55556a` | ~33% | Disabled, hint text, decorative text |

**Tailwind classes:** `text-white`, `text-text-secondary`, `text-text-muted`, `text-text-dim`

**Hierarchy rule:** Every label uses `text-text-muted`, every value uses `text-white` or its accent color. Never use `text-text-dim` for anything the user needs to read — only for decorative or timestamp elements.

### 2.4 Border Colors

| Pattern | Value | Usage |
|---------|-------|-------|
| Default border | `rgba(255, 255, 255, 0.08)` or `border-white/[0.08]` | Cards, containers, dividers |
| Subtle border | `rgba(255, 255, 255, 0.04)` or `border-white/[0.04]` | Row separators within tables |
| Accent border | `border-accent-lime/20` / `border-accent-sky/20` / `border-accent-coral/20` | Active/focused states, highlighted cards |
| Strong border | `rgba(255, 255, 255, 0.12)` or `border-white/[0.12]` | Floating popups, elevated overlays |
| Sidebar border | `rgba(255, 255, 255, 0.06)` or `border-white/[0.06]` | Sidebar edges, subtle separators |

### 2.5 Shadow / Glow Tokens

| Token | Value | Usage |
|-------|-------|-------|
| `shadow-glow-lime` | `0 0 20px rgba(188, 255, 95, 0.2)` | Lime button hover glow |
| `shadow-glow-sky` | `0 0 20px rgba(95, 201, 255, 0.2)` | Sky button hover glow |
| `shadow-glow-coral` | `0 0 20px rgba(255, 95, 126, 0.2)` | Coral emphasis glow |
| `shadow-glow-step` | `rgba(188, 255, 95, 0.125) 0px 0px 12px` | Subtle step glow for icon badges |
| `shadow-2xl` | Tailwind default | Floating popups, modals |

---

## 3. Typography

### 3.1 Font Stack

| Usage | Font | CSS Variable |
|-------|------|-------------|
| Body / UI | Geist Sans | `--font-geist-sans` |
| Numbers / Code | Geist Mono | `--font-geist-mono` |

**Import (Next.js):**
```tsx
import { Geist, Geist_Mono } from "next/font/google";
```

### 3.2 Type Scale

| Element | Size | Weight | Class | Font |
|---------|------|--------|-------|------|
| Page title | `text-xl` (20px) | `font-bold` | `text-white` | Sans |
| Section heading | `text-base` / `text-sm` | `font-semibold` / `font-bold` | `text-white` | Sans |
| Card value (large) | `text-2xl` / `text-3xl` | `font-bold` | `text-white` or accent | Mono |
| Card value (medium) | `text-lg` / `text-sm` | `font-bold` | accent color | Mono |
| Body text | `text-sm` / `text-xs` | `font-medium` | `text-text-secondary` | Sans |
| Label | `text-xs` / `text-[10px]` | `font-medium` / `font-semibold` | `text-text-muted` | Sans |
| Micro label | `text-[10px]` / `text-[9px]` | `font-semibold` | `text-text-muted` | Sans |
| Uppercase label | `text-[10px]` | `font-semibold` | `text-text-muted uppercase tracking-wider` | Sans |
| Monospace value | `text-xs` / `text-sm` | `font-bold` / `font-mono` | accent color | Mono |
| Table cell | `text-xs` | `font-mono` | `text-text-secondary` | Mono |

### 3.3 Number Formatting Rules

- **All numerical values** use `font-mono` (Geist Mono)
- Currency values: `$` prefix, 2 decimal places for ≥$1000, 4 decimal places for <$1000
- Percentages: 3 decimal places for rates (e.g., `0.060%`), 1-2 decimal places for display (e.g., `18.0%`)
- Tabular-nums: Use `font-variant-numeric: tabular-nums` or `font-number` class for aligned columns
- Positive PnL: `+` prefix with lime color; Negative PnL: `-` prefix with coral color

### 3.4 Uppercase Label Pattern

Labels above inputs, cards, and sections consistently use:
```
text-[10px] font-medium text-text-muted uppercase tracking-wider
```
Or for slightly larger labels:
```
text-xs font-medium text-text-muted uppercase tracking-wider
```

---

## 4. Spacing & Layout

### 4.1 Root Layout Structure

```
┌──────────────────────────────────────────────────────┐
│ h-dvh w-dvw overflow-hidden flex items-center        │
│ justify-center bg-bg-base noise-bg grid-pattern      │
│   ┌────────────────────────────────────────────────┐ │
│   │ h-full w-full lg:max-w-[1400px]                │ │
│   │ lg:border lg:border-white/[0.08]               │ │
│   │ lg:rounded-2xl overflow-hidden flex flex-col    │ │
│   │   ┌──────────┬─────────────────────────────┐   │ │
│   │   │ Sidebar  │  Main Content Area           │   │ │
│   │   │ 220px    │  flex-1 flex flex-col         │   │ │
│   │   │          │    ┌───────────────────────┐  │   │ │
│   │   │          │    │ MobileHeader (lg:hidden)│  │   │ │
│   │   │          │    ├───────────────────────┤  │   │ │
│   │   │          │    │ main (flex-1, scroll)  │  │   │ │
│   │   │          │    │   p-4 sm:p-6           │  │   │ │
│   │   │          │    ├───────────────────────┤  │   │ │
│   │   │          │    │ footer (shrink-0)      │  │   │ │
│   │   │          │    └───────────────────────┘  │   │ │
│   │   └──────────┴─────────────────────────────┘   │ │
│   └────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────┘
```

### 4.2 Spacing Scale

| Context | Padding | Gap |
|---------|---------|-----|
| Page content | `p-4 sm:p-6` | — |
| Card body | `p-4` / `p-5` / `p-6` | — |
| Card grid | — | `gap-3` / `gap-4` |
| Form groups | — | `space-y-4` / `space-y-5` |
| Inner card sections | — | `space-y-2.5` / `space-y-3` |
| Icon + text | — | `gap-1.5` / `gap-2` / `gap-3` |
| Button groups | — | `gap-3` |
| Table cell padding | `px-4 py-3` | `gap-3` |
| Footer | `px-4 sm:px-6 py-3` | — |

### 4.3 Max Content Width

- Desktop app: `lg:max-w-[1400px]` centered with `mx-auto`
- Form panels: `max-w-2xl mx-auto`
- Sidebar: `w-[220px] shrink-0`

---

## 5. Surface & Elevation

### 5.1 Surface Hierarchy

| Level | Background | Border | Usage |
|-------|-----------|--------|-------|
| 0 — Base | `bg-bg-base` | — | Page background |
| 1 — Surface | `bg-bg-surface` | `border-white/[0.08]` | Cards, panels, content blocks |
| 2 — Sidebar | `bg-bg-sidebar` | `border-white/[0.06]` | Navigation sidebar |
| 3 — Elevated | `bg-bg-elevated` or `bg-white/[0.06]` | `border-white/[0.08]` | Hover states, buttons, inputs |
| 4 — Overlay | `bg-bg-sidebar/[0.97] backdrop-blur-2xl` | `border-white/[0.12]` | Floating popups, modals |

### 5.2 Glassmorphism Pattern

For overlays, popups, and floating elements:
```css
background: bg-bg-sidebar/[0.97];
backdrop-filter: blur(16px); /* backdrop-blur-2xl */
border: 1px solid rgba(255, 255, 255, 0.12);
box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5); /* shadow-2xl */
border-radius: 1rem; /* rounded-2xl */
```

For content panels:
```css
background: bg-bg-surface/80;
backdrop-filter: blur(16px); /* backdrop-blur-xl */
border: 1px solid rgba(255, 255, 255, 0.08);
border-radius: 1rem; /* rounded-2xl */
```

---

## 6. Borders & Dividers

### 6.1 Border Radius

| Element | Radius | Class |
|---------|--------|-------|
| Cards, panels | 16px | `rounded-2xl` |
| Buttons, inputs | 12px | `rounded-xl` |
| Small buttons, badges | 8px | `rounded-lg` |
| Direction badges | 8px | `rounded-lg` |
| Icon containers | 8px-12px | `rounded-lg` / `rounded-xl` |
| Avatars, orbs | 50% | `rounded-full` |
| Keypad keys | 12px | `rounded-xl` (mobile), `rounded-lg` (desktop popup) |

### 6.2 Divider Pattern

Horizontal dividers between sections:
```html
<div className="w-full h-px bg-white/[0.06]" />
```

Row separators in tables:
```html
<!-- Applied as border on the row -->
className="border-b border-white/[0.04]"
```

---

## 7. Buttons & Controls

### 7.1 Primary Action Button (Lime)

```html
<button className="w-full bg-accent-lime text-bg-base h-12 rounded-xl text-base font-medium
  shadow-glow-lime hover:bg-[#d4ff99] transition-all duration-300
  disabled:opacity-50 disabled:cursor-not-allowed
  flex items-center justify-center gap-2">
  <Icon className="w-5 h-5" /> Label
</button>
```
- Background: `bg-accent-lime` (#BCFF5F)
- Text: `text-bg-base` (#1e1e24) — dark text on bright background
- Hover: Lighter lime `hover:bg-[#d4ff99]`
- Glow: `shadow-glow-lime`
- Height: `h-12` for primary, `h-11` for secondary

### 7.2 Secondary Action Button (Sky)

Same pattern as primary but:
- Background: `bg-accent-sky` (#5FC9FF)
- Glow: `shadow-glow-sky`
- No custom hover color (uses opacity)

### 7.3 Toggle Buttons

Active state:
```html
className="bg-accent-lime/10 border border-accent-lime/20 text-accent-lime
  py-2.5 rounded-xl font-medium text-sm flex items-center justify-center gap-2"
```

Inactive state:
```html
className="bg-bg-base border border-white/[0.08] text-text-muted
  hover:text-text-secondary transition-all duration-200
  py-2.5 rounded-xl font-medium text-sm flex items-center justify-center gap-2"
```

### 7.4 Icon Buttons

Small icon buttons in tables/lists:
```html
<button className="text-text-dim hover:text-accent-sky transition-colors p-1.5
  rounded-lg hover:bg-accent-sky/10" title="Edit">
  <Icon className="w-3.5 h-3.5" />
</button>
```

Destructive icon button:
```html
<button className="text-text-dim hover:text-accent-coral transition-colors p-1.5
  rounded-lg hover:bg-accent-coral/10" title="Delete">
  <Icon className="w-3.5 h-3.5" />
</button>
```

### 7.5 Ghost/Text Button

```html
<button className="flex items-center gap-1.5 text-xs text-text-muted
  hover:text-white transition-colors px-3 py-1.5 rounded-lg hover:bg-white/[0.04]">
  <Icon className="w-3 h-3" /> Label
</button>
```

### 7.6 Loading Spinner

```html
<div className="w-5 h-5 border-2 border-bg-base/30 border-t-bg-base
  rounded-full animate-spin" />
```
For smaller spinners: `w-3.5 h-3.5 border border-accent-coral/30 border-t-accent-coral`

---

## 8. Cards & Containers

### 8.1 Standard Card

```html
<div className="bg-bg-surface border border-white/[0.08] rounded-2xl p-5">
  <!-- Card header with icon -->
  <div className="flex items-center gap-2 mb-5">
    <div className="w-7 h-7 rounded-lg bg-accent-sky/5 border border-accent-sky/10
      flex items-center justify-center">
      <Icon className="w-3.5 h-3.5 text-accent-sky" />
    </div>
    <span className="text-xs font-medium text-text-muted uppercase tracking-wider">
      Card Title
    </span>
  </div>
  <!-- Card content -->
</div>
```

### 8.2 Stat Card (Small)

```html
<div className="bg-bg-surface border border-white/[0.08] rounded-2xl p-4
  flex flex-col justify-between">
  <div className="flex items-center gap-2 mb-3">
    <div className="w-7 h-7 rounded-lg bg-accent-lime/5 border border-accent-lime/10
      flex items-center justify-center">
      <span className="text-accent-lime"><Icon /></span>
    </div>
    <span className="text-[10px] font-medium text-text-muted uppercase tracking-wider">
      Label
    </span>
  </div>
  <div>
    <div className="text-lg font-bold font-mono text-white">Value</div>
    <div className="text-xs font-mono mt-1 text-accent-lime">Sub-value</div>
  </div>
</div>
```

### 8.3 Highlighted/Status Card

Conditional coloring based on state:
```html
<!-- Positive/Good -->
<div className="rounded-xl p-4 border bg-accent-lime/5 border-accent-lime/10">
  <CheckCircle2 className="w-4 h-4 text-accent-lime" />
  <span className="text-sm font-semibold text-white">Title</span>
  <p className="text-xs text-text-secondary">Description</p>
</div>

<!-- Warning -->
<div className="rounded-xl p-4 border bg-accent-sky/5 border-accent-sky/10">
  <Sparkles className="w-4 h-4 text-accent-sky" />
</div>

<!-- Danger -->
<div className="rounded-xl p-4 border bg-accent-coral/5 border-accent-coral/10">
  <AlertTriangle className="w-4 h-4 text-accent-coral" />
</div>
```

### 8.4 Icon Badge (Small Square)

Used next to titles and in sidebars:
```html
<div className="w-7 h-7 rounded-lg bg-accent-lime/5 border border-accent-lime/10
  flex items-center justify-center">
  <Icon className="w-3.5 h-3.5 text-accent-lime" />
</div>
```

Larger variant:
```html
<div className="w-9 h-9 rounded-xl bg-accent-lime/10 border border-accent-lime/20
  flex items-center justify-center shadow-glow-step">
  <Icon className="w-5 h-5 text-accent-lime" />
</div>
```

### 8.5 Decorative Glow (Inside Cards)

Subtle radial glow in top-right corner:
```html
<div className="absolute top-0 right-0 w-32 h-32 bg-accent-lime/5 rounded-full
  blur-[60px] pointer-events-none" />
```

Smaller variant:
```html
<div className="absolute top-0 right-0 w-10 h-10 bg-accent-lime/10 rounded-full
  blur-[20px] pointer-events-none" />
```

Centered placeholder glow:
```html
<div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2
  w-32 h-32 bg-accent-sky/5 rounded-full blur-[60px] pointer-events-none" />
```

---

## 9. Icons

### 9.1 Icon Library

**Lucide React** — consistent line-art icon style.

```tsx
import { IconName } from 'lucide-react';
```

### 9.2 Icon Size Conventions

| Context | Size | Class |
|---------|------|-------|
| Inside small badge | 14px | `w-3.5 h-3.5` |
| Inside label row | 14px | `w-3.5 h-3.5` |
| Inside button | 16-20px | `w-4 h-4` / `w-5 h-5` |
| Section icon | 16-20px | `w-4 h-4` / `w-5 h-5` |
| Empty state icon | 28-32px | `w-7 h-7` / `w-8 h-8` |
| Feature preview icon | 24-28px | `w-6 h-6` / `w-7 h-7` |

### 9.3 Icon Color Rules

- Icons inherit their context color: `text-accent-lime`, `text-accent-sky`, `text-accent-coral`, `text-text-muted`
- Icon badges use the accent at 5-10% opacity for background, 10-20% for border
- Never use raw white (`text-white`) for icons in labels — use `text-text-muted` or accent colors

### 9.4 Common Icon Assignments

| Concept | Icon | Accent |
|---------|------|--------|
| Profit / Gain / Positive | `ArrowUpRight` | Lime |
| Loss / Negative | `ArrowDownRight` | Coral |
| Money / Currency | `DollarSign` | Context |
| Percentage / Rate | `Percent` | Context |
| Chart / Volume | `BarChart3` | Sky |
| Calculator / Fees | `Calculator` | Sky |
| Target / Estimation | `Target` | Sky |
| Speed / Leverage | `Zap` | Context |
| Security / Confidence | `Shield` | Sky |
| Activity / Live | `Activity` | Lime |
| Success / Confirmed | `CheckCircle2` | Lime |
| Warning | `AlertTriangle` | Coral / Sky |
| Magic / AI / Smart | `Sparkles` | Sky |
| Time / History | `Clock` | Muted |
| Edit / Modify | `Pencil` | Sky |
| Delete / Remove | `Trash2` | Coral |
| Save / Confirm | `Save` | Sky |
| Refresh | `RotateCcw` | Muted |
| Eye / Live Preview | `Eye` | Sky |
| Keyboard | `Keyboard` | Sky |

---

## 10. Form Inputs & Keypads

### 10.1 Keypad Input (Tap-to-Enter)

A read-only button that opens a numeric keypad when tapped:

```html
<div>
  <label className="text-xs font-medium text-text-muted uppercase tracking-wider
    block mb-2">
    Label <span className="text-accent-coral ml-0.5">*</span>
  </label>
  <div className="relative">
    <span className="absolute left-3 top-1/2 -translate-y-1/2 pointer-events-none z-10">
      <Icon className="w-4 h-4 text-text-dim" />
    </span>
    <button
      className={`w-full h-11 bg-bg-base border rounded-xl pl-10 pr-4 text-sm font-mono
        text-left flex items-center cursor-pointer transition-all select-none overflow-hidden
        ${isActive
          ? 'border-accent-lime/40 ring-2 ring-accent-lime/15'
          : 'border-white/[0.08] hover:border-white/[0.15]'}`}
    >
      <span className={`truncate ${value ? 'text-white' : 'text-white/15'}`}>
        {value || placeholder}
      </span>
      {isActive && (
        <span className="inline-block w-[2px] h-5 bg-accent-sky animate-pulse
          ml-0.5 rounded-full shrink-0" />
      )}
    </button>
  </div>
</div>
```

**Active state:** Accent border + ring + blinking cursor
**Inactive state:** Subtle border, dimmed placeholder text

### 10.2 Numeric Keypad — Mobile (Bottom Sheet)

Slides up from the bottom on screens < 1024px:

```html
<!-- Backdrop -->
<div className="fixed inset-0 z-40 bg-black/20" onClick={onConfirm} />

<!-- Keypad panel -->
<div className="fixed bottom-0 left-0 right-0 z-50 bg-bg-sidebar/[0.97]
  backdrop-blur-2xl border-t border-white/[0.08] rounded-t-2xl shadow-2xl"
  style={{ paddingBottom: 'env(safe-area-inset-bottom, 16px)' }}>
  
  <!-- Display with label + value + cursor -->
  <div className="px-4 pt-3 pb-2 flex items-center justify-between border-b border-white/[0.06]">
    <div className="flex items-center gap-2">
      <Keyboard className="w-3.5 h-3.5 text-accent-sky" />
      <span className="text-[10px] text-text-muted uppercase tracking-wider font-semibold">
        {label}
      </span>
    </div>
    <div className="text-xl font-bold font-mono text-white min-w-[80px] text-right">
      {value || '0'}
      <span className="inline-block w-[2px] h-5 bg-accent-sky animate-pulse ml-0.5
        rounded-full align-middle" />
    </div>
  </div>
  
  <!-- 3x4 key grid -->
  <div className="grid grid-cols-3 gap-1.5 p-3 max-w-sm mx-auto">
    {keys.map(key => (
      <button className="h-14 rounded-xl text-xl font-mono font-semibold
        bg-white/[0.06] text-white hover:bg-white/[0.1]
        flex items-center justify-center transition-colors">
        {key === 'backspace' ? '⌫' : key}
      </button>
    ))}
  </div>
  
  <!-- Confirm button -->
  <div className="px-3 pb-2">
    <button className="w-full h-12 rounded-xl text-base font-medium
      bg-accent-lime text-bg-base active:scale-[0.98] shadow-lg">
      Confirm
    </button>
  </div>
</div>
```

Key layout: `['1','2','3','4','5','6','7','8','9','.','0','backspace']`

### 10.3 Numeric Keypad — Desktop (Floating Popup)

Appears near the input field as a positioned floating card:

```html
<!-- Backdrop (transparent click-away) -->
<div className="fixed inset-0 z-40" onClick={onConfirm} />

<!-- Popup -->
<div className="fixed z-50 bg-bg-sidebar/[0.97] backdrop-blur-2xl
  border border-white/[0.12] rounded-2xl shadow-2xl"
  style={{ top: popupPosition.top, left: popupPosition.left, width: 280 }}>
  
  <!-- Same internal structure as mobile but smaller -->
  <!-- h-11 keys instead of h-14, text-lg instead of text-xl -->
  <!-- Confirm button h-10 instead of h-12 -->
</div>
```

**Positioning logic:**
1. Default: below the input, left-aligned (`top = inputRect.bottom + 8`)
2. If overflows viewport bottom: above the input (`top = inputRect.top - keypadHeight - 8`)
3. If overflows right: shift left
4. Min left: 16px

### 10.4 Leverage Slider

```html
<div>
  <div className="flex items-center justify-between mb-2">
    <label className="text-xs font-medium text-text-muted uppercase tracking-wider">
      Leverage
    </label>
    <div className="flex items-center gap-1.5">
      <input type="number" min="1" max="200" step="1"
        className="w-16 h-7 text-center text-xs font-mono rounded-lg border
          bg-accent-lime/10 border-accent-lime/20 text-accent-lime
          focus:border-accent-lime/30 focus:ring-accent-lime/10 focus:ring-2
          outline-none transition-all bg-transparent" />
      <span className="text-xs text-text-muted font-mono">x</span>
    </div>
  </div>
  <input type="range" min="1" max="50" step="1"
    className="w-full h-2 rounded-full appearance-none cursor-pointer"
    style={{
      background: `linear-gradient(to right, #BCFF5F 0%, #BCFF5F ${percent}%,
        rgba(255,255,255,0.06) ${percent}%, rgba(255,255,255,0.06) 100%)`
    }} />
</div>
```

### 10.5 Input Mode Toggle (Margin / Volume)

```html
<div className="grid grid-cols-2 gap-2">
  <button className={`py-2 rounded-xl font-medium text-xs flex items-center
    justify-center gap-1.5 transition-all border
    ${active ? 'bg-accent-lime/10 border-accent-lime/20 text-accent-lime'
             : 'bg-bg-base border border-white/[0.08] text-text-muted'}`}>
    <DollarSign className="w-3.5 h-3.5" /> Margin
  </button>
  <!-- Same for Volume with BarChart3 icon -->
</div>
```

---

## 11. Modals & Overlays

### 11.1 Full Edit Modal

```html
<!-- Backdrop -->
<div className="fixed inset-0 z-30 bg-black/40 backdrop-blur-sm" onClick={onClose} />

<!-- Modal -->
<div className="fixed inset-4 sm:inset-auto sm:left-1/2 sm:top-1/2
  sm:-translate-x-1/2 sm:-translate-y-1/2 sm:w-full sm:max-w-lg z-40
  bg-bg-sidebar/[0.98] backdrop-blur-2xl border border-white/[0.1]
  rounded-2xl shadow-2xl overflow-y-auto max-h-[90vh] custom-scrollbar">
  <div className="p-5 sm:p-6">
    <!-- Header with icon + title + close button -->
    <!-- Form content -->
    <!-- Action buttons -->
  </div>
</div>
```

**Key rules:**
- Backdrop uses `bg-black/40 backdrop-blur-sm` for frosted-glass effect
- Modal has stronger border `border-white/[0.1]`
- Responsive: full-bleed on mobile (`inset-4`), centered card on desktop
- Max height with scroll: `max-h-[90vh] overflow-y-auto custom-scrollbar`
- Close via backdrop click, X button, or Escape key
- Wrap in `<AnimatePresence>` for enter/exit animations

### 11.2 Confirmation / Simple Overlay

For simpler overlays (like the keypad backdrop):
- Backdrop: `fixed inset-0 z-40 bg-black/20`
- Click backdrop to dismiss

---

## 12. Tables & Lists

### 12.1 Data Table (Desktop)

```html
<div className="bg-bg-surface border border-white/[0.08] rounded-2xl overflow-hidden">
  <!-- Header -->
  <div className="grid grid-cols-[auto_1fr_1fr_1fr_1fr_1fr_1fr_auto_auto] gap-3
    px-4 py-3 border-b border-white/[0.08] bg-bg-sidebar/50
    text-xs font-semibold text-text-muted uppercase tracking-wider">
    <span>Col1</span><span>Col2</span>...
  </div>
  <!-- Scrollable body -->
  <div className="max-h-[calc(100dvh-320px)] overflow-y-auto custom-scrollbar">
    {rows.map(row => (
      <div className="grid grid-cols-[...] gap-3 px-4 py-3
        border-b border-white/[0.04] items-center
        hover:bg-white/[0.02] transition-colors">
        <!-- Cells -->
      </div>
    ))}
  </div>
</div>
```

### 12.2 Responsive Table

Show full table on desktop, compact layout on mobile:
```html
<!-- Desktop row -->
<div className="hidden md:grid grid-cols-[...] ...">
  <!-- Full data -->
</div>
<!-- Mobile row -->
<div className="md:hidden grid grid-cols-[auto_1fr_1fr_auto_auto] ...">
  <!-- Condensed data -->
</div>
```

### 12.3 Direction Badge

```html
<span className={`inline-flex items-center gap-1 text-[11px] font-medium
  px-2 py-0.5 rounded-lg
  ${direction === 'long'
    ? 'bg-accent-lime/10 text-accent-lime'
    : 'bg-accent-coral/10 text-accent-coral'}`}>
  <TrendingUp className="w-3 h-3" /> long
</span>
```

Mobile variant (icon only):
```html
<span className="inline-flex items-center gap-0.5 text-[10px] font-medium
  px-1.5 py-0.5 rounded-lg ...">
  <TrendingUp className="w-2.5 h-2.5" />
</span>
```

---

## 13. Animations & Transitions

### 13.1 Library

**Framer Motion** for all complex animations.

```tsx
import { motion, AnimatePresence } from 'framer-motion';
```

### 13.2 Page Transitions

Wrap each section in `<AnimatePresence mode="wait">`:
```tsx
<AnimatePresence mode="wait">
  {activeSection === 'dashboard' && (
    <motion.div key="dashboard"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.2 }}>
      <DashboardSection />
    </motion.div>
  )}
</AnimatePresence>
```

### 13.3 Stagger Container

For cards/elements that appear one by one:
```tsx
const staggerContainer = {
  animate: { transition: { staggerChildren: 0.05 } },
};

const staggerItem = {
  initial: { opacity: 0, y: 8 },
  animate: { opacity: 1, y: 0, transition: { duration: 0.25 } },
};

// Usage
<motion.div variants={staggerContainer} initial="initial" animate="animate">
  <motion.div variants={staggerItem}>Card 1</motion.div>
  <motion.div variants={staggerItem}>Card 2</motion.div>
</motion.div>
```

### 13.4 Modal Animation

```tsx
<motion.div
  initial={{ opacity: 0, scale: 0.95, y: 20 }}
  animate={{ opacity: 1, scale: 1, y: 0 }}
  exit={{ opacity: 0, scale: 0.95, y: 20 }}
  transition={{ type: 'spring', damping: 25, stiffness: 300 }}>
```

### 13.5 Keypad Animation

**Mobile (bottom sheet):**
```tsx
<motion.div
  initial={{ y: '100%' }}
  animate={{ y: 0 }}
  exit={{ y: '100%' }}
  transition={{ type: 'spring', damping: 28, stiffness: 300 }}>
```

**Desktop (floating popup):**
```tsx
<motion.div
  initial={{ opacity: 0, scale: 0.9, y: 8 }}
  animate={{ opacity: 1, scale: 1, y: 0 }}
  exit={{ opacity: 0, scale: 0.9, y: 8 }}
  transition={{ type: 'spring', damping: 25, stiffness: 300 }}>
```

### 13.6 Progress Bar Animation

```tsx
<motion.div
  className="h-full rounded-full bg-accent-lime"
  initial={{ width: 0 }}
  animate={{ width: `${percent}%` }}
  transition={{ duration: 1, ease: 'easeOut' }}
/>
```

### 13.7 Keypress Micro-Interaction

```tsx
<motion.button
  whileTap={{ scale: 0.9 }}  // Mobile
  whileTap={{ scale: 0.92 }} // Desktop popup (smaller)
>
```

### 13.8 Button Press

```tsx
<button className="active:scale-[0.98] transition-all">
```

### 13.9 Live/Active Indicator (Pulse)

```html
<span className="relative flex h-2 w-2">
  <span className="animate-ping absolute inline-flex h-full w-full rounded-full
    bg-accent-sky opacity-75" />
  <span className="relative inline-flex rounded-full h-2 w-2 bg-accent-sky" />
</span>
```

With text label:
```html
<span className="text-[9px] font-bold text-accent-sky uppercase tracking-widest">
  LIVE
</span>
```

### 13.10 Blinking Cursor

```html
<span className="inline-block w-[2px] h-5 bg-accent-sky animate-pulse
  ml-0.5 rounded-full" />
```

### 13.11 Animated Top Border (Live Preview)

```html
<div className="absolute top-0 left-0 w-full h-1
  bg-gradient-to-r from-transparent via-accent-sky/20 to-transparent
  animate-pulse" />
```

### 13.12 Standard Transition Classes

| Property | Duration | Class |
|----------|----------|-------|
| Color change | 200ms | `transition-colors` |
| All properties | 200ms | `transition-all duration-200` |
| Hover effects | 300ms | `transition-all duration-300` |
| Value changes | 200ms | `transition-all duration-200` (on data display elements) |

---

## 14. Background Effects

### 14.1 Noise Texture

Applied to the root container:
```css
.noise-bg::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: url("data:image/svg+xml,...feTurbulence...");
  pointer-events: none;
  z-index: 0;
  border-radius: inherit;
  overflow: hidden;
}
```
Provides a subtle grain texture at 3% opacity.

### 14.2 Grid Dot Pattern

Applied to the root container alongside noise:
```css
.grid-pattern::after {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px);
  background-size: 24px 24px;
  pointer-events: none;
  z-index: 0;
  border-radius: inherit;
  overflow: hidden;
}
```
Creates a subtle dot grid at 3% opacity.

### 14.3 Ambient Glow Orbs

Three floating blurred circles that slowly animate:

| Orb | Color | Size | Blur | Position | Animation |
|-----|-------|------|------|----------|-----------|
| Lime | `rgba(188, 255, 95, 0.05)` | 256px | 100px | Top-left | `orb-float 8s ease-in-out infinite` |
| Sky | `rgba(95, 201, 255, 0.05)` | 256px | 100px | Bottom-right | `orb-float 10s ease-in-out infinite reverse` |
| Coral | `rgba(255, 95, 126, 0.03)` | 384px | 120px | Center | Static |

```css
@keyframes orb-float {
  0%, 100% { transform: translate(0, 0); }
  50% { transform: translate(10px, -10px); }
}
```

All orbs: `pointer-events: none; z-index: 0;`

### 14.4 In-Card Glow Accents

Small blurred circles placed absolutely inside cards for visual interest:
```html
<div className="absolute top-0 right-0 w-32 h-32 bg-accent-lime/5
  rounded-full blur-[60px] pointer-events-none" />
```

---

## 15. Scrollbars

Custom dark-themed scrollbars for all scrollable regions:

```css
.custom-scrollbar::-webkit-scrollbar { width: 6px; }
.custom-scrollbar::-webkit-scrollbar-track { background: transparent; }
.custom-scrollbar::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.1);
  border-radius: 9999px;
}
.custom-scrollbar::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}
```

Apply to all scrollable containers: `overflow-y-auto custom-scrollbar`

---

## 16. Navigation Patterns

### 16.1 Desktop Sidebar

```
┌──────────────────┐
│ Logo + Brand     │  h-16, border-b
├──────────────────┤
│ NAVIGATION       │  text-[10px] uppercase dim
│ ● Dashboard      │  active: lime bg/border/text
│ ○ New Trade      │  inactive: muted text
│ ○ Trades         │
│ ○ Calculations   │
│ ○ Estimator      │
├──────────────────┤
│ Quick Stats      │  (optional, when data exists)
│ Total Trades: 12 │
│ Total Fees: $45  │
│ Avg Fee: 0.06%   │
│ Confidence: 72%  │
└──────────────────┘
```

Width: `w-[220px]` — hidden on mobile (`max-lg:hidden`)

Active nav item:
```html
<button className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl
  bg-accent-lime/7 border border-accent-lime/14.5 text-accent-lime
  transition-all duration-200">
  <Icon /> <span className="text-sm font-medium">Label</span>
</button>
```

Inactive nav item:
```html
<button className="w-full flex items-center gap-3 px-3 py-2.5 rounded-xl
  text-text-muted hover:text-text-secondary hover:bg-white/[0.04]
  border border-transparent transition-all duration-200">
  <Icon /> <span className="text-sm font-medium">Label</span>
</button>
```

### 16.2 Mobile Header + Tab Bar

**Header bar** (h-14):
```html
<div className="lg:hidden h-14 bg-bg-sidebar border-b border-white/[0.06]
  flex items-center justify-between px-4">
  <div className="flex items-center gap-2">
    <div className="w-8 h-8 rounded-lg bg-accent-lime/10 border border-accent-lime/20
      flex items-center justify-center">
      <CircleDollarSign className="w-4 h-4 text-accent-lime" />
    </div>
    <span className="text-sm font-bold text-white">AppName</span>
  </div>
  <button className="w-8 h-8 rounded-lg flex items-center justify-center
    text-text-muted hover:text-white hover:bg-white/[0.08]">
    <Menu className="w-4 h-4" />
  </button>
</div>
```

**Tab bar** below header:
```html
<div className="flex border-b border-white/[0.06] bg-bg-sidebar overflow-x-auto">
  {tabs.map(tab => (
    <button className={`flex-1 flex items-center justify-center gap-1 py-3
      text-[11px] font-medium whitespace-nowrap
      ${active ? 'text-accent-lime border-b-2 border-accent-lime'
               : 'text-text-muted'}`}>
      <Icon className="w-4 h-4" /> <span>Label</span>
    </button>
  ))}
</div>
```

---

## 17. Responsive Breakpoints

| Breakpoint | Width | Usage |
|-----------|-------|-------|
| Default | 0px | Mobile layout (tab bar, compact cards) |
| `sm` | 640px | Slightly larger padding, multi-column stats |
| `md` | 768px | 2-column grids, desktop table headers |
| `lg` | 1024px | Sidebar appears, mobile header hides |
| `xl` | 1280px | Wider content areas |

### Key Responsive Rules:
- Sidebar: `max-lg:hidden` (hidden below 1024px)
- Mobile header: `lg:hidden` (hidden above 1024px)
- Keypad: bottom sheet below 1024px, floating popup above
- Table: compact mobile row / full desktop row
- Page padding: `p-4 sm:p-6`
- Footer padding: `px-4 sm:px-6`

---

## 18. Component Reference

### 18.1 Component Architecture

```
Page (Root)
├── Sidebar (desktop only)
├── MobileHeader + TabBar (mobile only)
├── Main Content (scrollable)
│   ├── Page Title Row
│   └── AnimatePresence (section switch)
│       ├── DashboardSection
│       │   ├── StatCard (repeated)
│       │   ├── HighlightedCard (conditional)
│       │   └── EmptyState
│       ├── NewTradeSection
│       │   ├── DirectionToggle
│       │   ├── KeypadInput (repeated)
│       │   ├── LeverageSlider
│       │   ├── InputModeToggle
│       │   ├── LiveTradeCalc
│       │   └── NumericKeypad (floating/bottom-sheet)
│       ├── TradesSection
│       │   ├── DataTable (responsive)
│       │   ├── EditModal
│       │   │   └── KeypadInput (repeated)
│       │   └── NumericKeypad
│       ├── CalculationsSection
│       └── EstimatorSection
│           ├── Form with KeypadInputs
│           ├── LiveTradeCalc
│           └── NumericKeypad
└── Footer (sticky)
```

### 18.2 Empty State Pattern

```html
<motion.div initial={{ opacity: 0, y: 12 }} animate={{ opacity: 1, y: 0 }}
  className="text-center py-12">
  <div className="w-16 h-16 mx-auto rounded-2xl bg-accent-lime/5
    border border-accent-lime/10 flex items-center justify-center mb-4">
    <Icon className="w-8 h-8 text-accent-lime/30" />
  </div>
  <h3 className="text-lg font-semibold text-white mb-2">No Items Yet</h3>
  <p className="text-sm text-text-muted mb-6 max-w-md mx-auto">
    Description of what happens when items are added.
  </p>
  <button className="inline-flex items-center gap-2 bg-accent-lime text-bg-base
    px-6 h-12 rounded-xl text-base font-medium shadow-glow-lime
    hover:bg-[#d4ff99] transition-all duration-300">
    <Icon className="w-5 h-5" /> Call to Action
  </button>
</motion.div>
```

### 18.3 Key / Value Row Pattern

For data display in breakdowns and details:
```html
<div className="flex items-center justify-between">
  <span className="text-xs text-text-muted">Label</span>
  <span className="font-mono text-sm text-white">Value</span>
</div>
```

With icon:
```html
<div className="flex items-center justify-between">
  <span className="text-xs text-text-muted flex items-center gap-1.5">
    <Icon className="w-3 h-3" /> Label
  </span>
  <span className="font-mono text-sm text-accent-sky">Value</span>
</div>
```

---

## 19. Tailwind Theme Configuration

Complete theme tokens for `globals.css`:

```css
@theme inline {
  /* Backgrounds */
  --color-bg-base: #1e1e24;
  --color-bg-surface: #28282f;
  --color-bg-sidebar: #242430;
  --color-bg-elevated: #333340;

  /* Accent neon colors */
  --color-accent-lime: #BCFF5F;
  --color-accent-sky: #5FC9FF;
  --color-accent-coral: #FF5F7E;

  /* Text hierarchy */
  --color-text-secondary: #c8c8d4;
  --color-text-muted: #8888a0;
  --color-text-dim: #55556a;

  /* Glow shadows */
  --shadow-glow-lime: 0 0 20px rgba(188, 255, 95, 0.2);
  --shadow-glow-sky: 0 0 20px rgba(95, 201, 255, 0.2);
  --shadow-glow-coral: 0 0 20px rgba(255, 95, 126, 0.2);
  --shadow-glow-step: rgba(188, 255, 95, 0.125) 0px 0px 12px;
}

:root {
  --radius: 0.625rem;
  --background: #1e1e24;
  --foreground: #ffffff;
  --card: #28282f;
  --card-foreground: #ffffff;
  --primary: #BCFF5F;
  --primary-foreground: #1e1e24;
  --secondary: #333340;
  --secondary-foreground: #c8c8d4;
  --muted: #333340;
  --muted-foreground: #8888a0;
  --accent: #5FC9FF;
  --accent-foreground: #1e1e24;
  --destructive: #FF5F7E;
  --border: rgba(255, 255, 255, 0.08);
  --input: rgba(255, 255, 255, 0.08);
  --ring: rgba(188, 255, 95, 0.3);
  --chart-1: #BCFF5F;
  --chart-2: #5FC9FF;
  --chart-3: #FF5F7E;
  --chart-4: #c8c8d4;
  --chart-5: #55556a;
  --sidebar: #242430;
  --sidebar-foreground: #c8c8d4;
  --sidebar-primary: #BCFF5F;
  --sidebar-primary-foreground: #1e1e24;
  --sidebar-accent: rgba(188, 255, 95, 0.07);
  --sidebar-accent-foreground: #BCFF5F;
  --sidebar-border: rgba(255, 255, 255, 0.06);
  --sidebar-ring: rgba(188, 255, 95, 0.3);
}
```

---

## 20. Anti-Patterns & Rules

### DO:
- ✅ Use `font-mono` for ALL numerical values
- ✅ Use accent colors at low opacity (`/5`, `/10`, `/20`) for backgrounds
- ✅ Use `backdrop-blur` on overlays and floating elements
- ✅ Add `pointer-events-none` to decorative elements
- ✅ Use `custom-scrollbar` on all scrollable regions
- ✅ Use `overflow-hidden` on the root container to prevent page scroll
- ✅ Apply `noise-bg grid-pattern` to the root for texture
- ✅ Use `AnimatePresence` for enter/exit animations
- ✅ Make footer `shrink-0` with `mt-auto` in a flex column
- ✅ Use `max-h-[calc(100dvh-...)]` for scrollable list regions
- ✅ Use `dvh` units instead of `vh` for mobile viewport handling
- ✅ Add `transition-all` or `transition-colors` to all interactive elements
- ✅ Use `rounded-2xl` for cards, `rounded-xl` for buttons/inputs

### DON'T:
- ❌ Never use indigo or blue as a primary color
- ❌ Never use solid opaque backgrounds for overlays (always use blur + transparency)
- ❌ Never use `text-white` for labels (use `text-text-muted`)
- ❌ Never leave interactive elements without hover/focus transitions
- ❌ Never use `position: sticky` on footers (use flex + `mt-auto`)
- ❌ Never use `overflow: auto` without `custom-scrollbar`
- ❌ Never use raw hex colors inline — use the design tokens
- ❌ Never animate layout properties (width/height) with Framer Motion on frequently-updated values
- ❌ Never use `h-screen` (use `h-dvh` for dynamic viewport height)
- ❌ Never create new accent colors outside the lime/sky/coral system
- ❌ Never use emoji in production UI unless explicitly requested
- ❌ Never use `font-sans` for numbers (always `font-mono`)

---

## Quick Reference Cheat Sheet

```
Colors:     #1e1e24 (base)  #28282f (surface)  #242430 (sidebar)  #333340 (elevated)
Accents:    #BCFF5F (lime)  #5FC9FF (sky)      #FF5F7E (coral)
Text:       #ffffff (primary) #c8c8d4 (secondary) #8888a0 (muted) #55556a (dim)
Borders:    white/[0.04] (subtle)  white/[0.08] (default)  white/[0.12] (strong)
Radius:     16px (cards)  12px (buttons)  8px (badges)
Font:       Geist Sans (body)  Geist Mono (numbers)
Icons:      Lucide React (line-art style)
Animation:  Framer Motion (springs, staggers, presence)
Layout:     Sidebar + scrollable main + sticky footer
Responsive: Mobile-first, sidebar at lg:1024px
```

---

*This design system is optimized for dark-themed data-dense applications — dashboards, trading tools, analytics platforms, and financial applications. Adapt the accent colors and icon assignments to match your domain while preserving the surface hierarchy, glass-morphism patterns, and animation language.*
