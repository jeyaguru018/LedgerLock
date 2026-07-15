---
name: LedgerLock
colors:
  surface: '#0d150e'
  surface-dim: '#0d150e'
  surface-bright: '#323c33'
  surface-container-lowest: '#081009'
  surface-container-low: '#151e16'
  surface-container: '#19221a'
  surface-container-high: '#232c24'
  surface-container-highest: '#2e372e'
  on-surface: '#dbe5d9'
  on-surface-variant: '#bacbb9'
  inverse-surface: '#dbe5d9'
  inverse-on-surface: '#29332a'
  outline: '#859585'
  outline-variant: '#3b4a3d'
  surface-tint: '#00e475'
  primary: '#75ff9e'
  on-primary: '#003918'
  primary-container: '#00e676'
  on-primary-container: '#00612e'
  inverse-primary: '#006d35'
  secondary: '#c6c6c7'
  on-secondary: '#2f3131'
  secondary-container: '#454747'
  on-secondary-container: '#b4b5b5'
  tertiary: '#dee5f2'
  on-tertiary: '#2a313b'
  tertiary-container: '#c2c9d5'
  on-tertiary-container: '#4d545f'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#62ff96'
  primary-fixed-dim: '#00e475'
  on-primary-fixed: '#00210b'
  on-primary-fixed-variant: '#005226'
  secondary-fixed: '#e2e2e2'
  secondary-fixed-dim: '#c6c6c7'
  on-secondary-fixed: '#1a1c1c'
  on-secondary-fixed-variant: '#454747'
  tertiary-fixed: '#dce3f0'
  tertiary-fixed-dim: '#c0c7d3'
  on-tertiary-fixed: '#151c25'
  on-tertiary-fixed-variant: '#404752'
  background: '#0d150e'
  on-background: '#dbe5d9'
  surface-variant: '#2e372e'
typography:
  display-lg:
    fontFamily: Sora
    fontSize: 48px
    fontWeight: '700'
    lineHeight: '1.1'
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Sora
    fontSize: 32px
    fontWeight: '600'
    lineHeight: '1.2'
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Sora
    fontSize: 24px
    fontWeight: '600'
    lineHeight: '1.3'
  headline-sm:
    fontFamily: Sora
    fontSize: 20px
    fontWeight: '600'
    lineHeight: '1.4'
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: '1.6'
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: '1.6'
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: '1.5'
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: '1'
    letterSpacing: 0.05em
  numeric-data:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '500'
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 8px
  xs: 4px
  sm: 12px
  md: 24px
  lg: 48px
  xl: 80px
  gutter: 24px
  margin-mobile: 16px
  margin-desktop: 40px
---

## Brand & Style

This design system is built on the narrative of "Precision Trust." It targets a high-stakes financial demographic that values speed, accuracy, and technical clarity. The aesthetic is a refined blend of **Minimalism** and **Modern Corporate**, drawing inspiration from "control room" interfaces where every pixel serves a functional purpose.

The visual language is characterized by deep monochromatic backgrounds, high-contrast typography, and vibrant functional accents. It avoids decorative flourishes, opting instead for structural integrity through 1px borders, generous whitespace, and a rigid adherence to a technical, data-forward layout. The goal is to evoke a sense of absolute security and professional-grade performance.

**Global Disclaimer:** All layouts must include a subtle disclaimer in the footer or a specialized banner: *"LedgerLock is a demonstration system for educational purposes. No real currency is transacted."*

## Colors

The palette is anchored in a high-contrast dark mode to reduce eye strain during deep data analysis.

- **Primary (Electric Green):** Used exclusively for primary actions, success states, and critical growth indicators. It should be used sparingly to maintain its "active" signal strength.
- **Surface & Background:** The core interface uses `#0A0B0D`. Secondary surfaces (cards, modals) should use a slightly elevated tint or a 1px border to distinguish from the background.
- **Typography:** Primary text is pure white (`#FFFFFF`) to ensure maximum legibility. Secondary and metadata text utilizes the muted gray (`#9CA3AF`).
- **Semantic Colors:** Error and Warning states use high-chroma values (`#FF4757`, `#FFB800`) to stand out instantly against the dark canvas.

## Typography

The typography system prioritizes structural hierarchy and legibility.

- **Headlines:** Sora provides a geometric, technical feel for headers. Use it for branding and major section titles.
- **Body:** Inter is used for all UI text and descriptions due to its neutrality and excellent readability at small sizes.
- **Currency & Data:** All numerical values must use **tabular numerals** (tnum) to ensure that columns of numbers align perfectly in ledgers and tables.
- **Mobile Scaling:** For screens under 768px, `display-lg` should scale down to 32px and `headline-lg` to 24px to maintain layout integrity.

## Layout & Spacing

This design system utilizes a **12-column fluid grid** for desktop and a **4-column grid** for mobile. 

- **The 8px Rule:** All spacing and sizing must be multiples of 8px (4px for micro-adjustments).
- **Margins:** Desktop layouts require generous 40px outer margins to create a "premium" sense of space. Mobile margins are tightened to 16px.
- **Data Density:** While the overall brand uses generous whitespace, data tables and ledgers should maintain a compact vertical rhythm (8px or 12px padding) to allow for maximum information visibility without scrolling.

## Elevation & Depth

In this system, depth is conveyed through **Tonal Layering** and **1px Borders** rather than traditional shadows.

- **Tier 0 (Background):** `#0A0B0D` - The base canvas.
- **Tier 1 (Cards/Surfaces):** A subtle lift using `#141518` with a 1px stroke of `#2D2F34`.
- **Tier 2 (Popovers/Modals):** High-contrast elevation using a 1px stroke of `#3F424A` and a very subtle, deep black ambient shadow (0px 8px 24px rgba(0,0,0,0.5)) to separate the element from the stack.
- **Interactive States:** Hovering over a card or list item should brighten the border color or slightly lighten the background fill.

## Shapes

The shape language is "Technical-Soft." Elements are fundamentally rectangular to imply stability, but corners are softened to prevent the UI from feeling aggressive.

- **Standard Elements:** Buttons, inputs, and small widgets use a **8px** radius.
- **Large Containers:** Cards and modals use a **16px** radius for a more distinct structural presence.
- **Borders:** All borders must be exactly 1px. Use a muted gray-scale for borders (e.g., `#2D2F34`) to ensure they define shape without distracting from content.

## Components

- **Buttons:** Primary buttons use the Electric Green background with black text. Secondary buttons use a transparent background with a 1px white border. All buttons use 16px horizontal and 12px vertical padding.
- **Inputs:** Dark backgrounds (`#0A0B0D`) with a 1px border. On focus, the border transitions to Electric Green. Use `label-md` for field labels.
- **Chips/Status Tags:** Small, low-profile badges. Success uses a light green tint with green text; Error uses a light red tint with red text. No heavy backgrounds.
- **Data Tables:** Row lines should be subtle (`#1F2023`). Header cells use `label-md` with `9CA3AF` text color. Ensure tabular numerals are active for all value columns.
- **Cards:** Use for dashboard widgets. Each card should have a 1px border and a clearly defined header section using `headline-sm`.
- **Disclaimers:** Footer notes should be set in `body-sm` with the muted gray color to remain present but non-intrusive.