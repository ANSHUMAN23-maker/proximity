---
name: Proximity Design System
colors:
  surface: '#f7f9fb'
  surface-dim: '#d8dadc'
  surface-bright: '#f7f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f2f4f6'
  surface-container: '#eceef0'
  surface-container-high: '#e6e8ea'
  surface-container-highest: '#e0e3e5'
  on-surface: '#191c1e'
  on-surface-variant: '#3d4946'
  inverse-surface: '#2d3133'
  inverse-on-surface: '#eff1f3'
  outline: '#6d7a76'
  outline-variant: '#bdc9c4'
  surface-tint: '#006b5b'
  primary: '#006859'
  on-primary: '#ffffff'
  primary-container: '#008471'
  on-primary-container: '#f4fffa'
  inverse-primary: '#71d8c2'
  secondary: '#565e74'
  on-secondary: '#ffffff'
  secondary-container: '#dae2fd'
  on-secondary-container: '#5c647a'
  tertiary: '#006947'
  on-tertiary: '#ffffff'
  tertiary-container: '#00855b'
  on-tertiary-container: '#f5fff6'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#8ef5de'
  primary-fixed-dim: '#71d8c2'
  on-primary-fixed: '#00201a'
  on-primary-fixed-variant: '#005144'
  secondary-fixed: '#dae2fd'
  secondary-fixed-dim: '#bec6e0'
  on-secondary-fixed: '#131b2e'
  on-secondary-fixed-variant: '#3f465c'
  tertiary-fixed: '#6ffbbe'
  tertiary-fixed-dim: '#4edea3'
  on-tertiary-fixed: '#002113'
  on-tertiary-fixed-variant: '#005236'
  background: '#f7f9fb'
  on-background: '#191c1e'
  surface-variant: '#e0e3e5'
typography:
  display-currency:
    fontFamily: Inter
    fontSize: 40px
    fontWeight: '700'
    lineHeight: 48px
    letterSpacing: -0.03em
  display-currency-mobile:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.01em
  headline-sm:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '400'
    lineHeight: 22px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Inter
    fontSize: 15px
    fontWeight: '600'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 13px
    fontWeight: '600'
    lineHeight: 18px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 14px
    letterSpacing: 0.02em
  keypad-num:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '500'
    lineHeight: 28px
  keypad-sub:
    fontFamily: Inter
    fontSize: 10px
    fontWeight: '600'
    lineHeight: 12px
    letterSpacing: 0.08em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1.25rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

This design system is tailored for an offline-first peer-to-peer (P2P) and UPI mobile payments experience. The aesthetic merges utility-focused modern fintech with calm, approachable reliability. Because offline financial transactions require heightened trust and immediate visual clarity, the interface relies on deliberate, uncluttered layouts, strong color feedback, and explicit states.

The design movement is **Modern Functional Fintech**:
- **Clarity over ornament:** Generous tap targets, zero visual noise, and unambiguous typographic hierarchy designed for split-second verification in physical environments (retail counters, transit, low-connectivity zones).
- **Security & Reassurance:** Deep teal accents convey institutional stability and technical confidence without feeling bureaucratic or cold.
- **Physical-Digital Bridge:** Clean keypad grids, prominent numerical displays, high-contrast QR framing, and receipt-style confirmation modules evoke familiar transactional patterns.

## Colors

The palette establishes an immediate sense of financial security and clarity.

- **Primary Teal (`#008774`) & Interactive Teal (`#007A68`):** The foundational brand and action color. Used for primary call-to-action buttons, key status badges, active tab markers, and success highlights.
- **Secondary Slate (`#0F172A`):** The primary text and high-contrast structural anchor. Paired with muted slates (`#475569` for secondary text, `#94A3B8` for placeholders).
- **Tertiary Emerald (`#10B981`):** Functional success state for transaction verifications, completed checkmarks, and positive balances.
- **Error & Destructive Red (`#DC2626` / `#B91C1C`):** Reserved for transaction cancellations, critical warnings, and destructive actions like clearing app data.
- **Neutral Canvas (`#F8FAFC` to `#FFFFFF`):** High-efficiency neutral background pairing pure white (`#FFFFFF`) card surfaces against an ultra-soft cool gray backdrop (`#F8FAFC` / `#F1F5F9`) with delicate hairline borders (`#E2E8F0`).

## Typography

The type system is powered by **Inter**, leveraging tabular figures and geometric precision for monetary values, phone numbers, and transactional identifiers.

- **Financial Display:** Monetary amounts use bold, tightly tracked numbers (`display-currency`) to ensure amounts are impossible to misread at a glance.
- **Keypad Hierarchy:** Custom numbers feature distinct primary glyphs paired with uppercase secondary alpha cues (`keypad-sub`) for intuitive dialer feel.
- **Labels & Microcopy:** Clean, medium-weight labels provide secondary structure without competing against values or transaction titles.

## Layout & Spacing

The layout is built for mobile ergonomics:
- **Mobile Container:** 390px to 428px standard viewports with fixed lateral margins of `1.25rem` (20px) and bottom safe-area compliance for modern bezel-less devices.
- **Form & Flow Rhythm:** Vertical stack rhythm operates strictly on multiples of 4px/8px: `0.5rem` between field labels and inputs, `1rem` between stacked cards, and `1.5rem` separating core flow sections.
- **Thumb Zone Optimization:** Interactive actions—such as primary submit buttons, dial keys, and navigation tabs—are positioned in the bottom 40% of the screen.

## Elevation & Depth

Visual hierarchy favors clean structural surface segmentation over heavy drop shadows.

- **Level 0 (Canvas):** Background base tinted with `#F8FAFC`.
- **Level 1 (Cards & Groups):** Pure `#FFFFFF` surface bordered by a crisp `1px` stroke of `#E2E8F0`. Low-intensity ambient shadow: `box-shadow: 0 1px 3px rgba(15, 23, 42, 0.04)`.
- **Level 2 (Modals, Floating Bars, Bottom Nav):** Floating navigation and bottom sheets use a elevated white surface with `box-shadow: 0 -4px 16px rgba(15, 23, 42, 0.06)` and a subtle top border `#F1F5F9`.
- **Level 3 (Focused Inputs & Alerts):** Active inputs adopt an interactive primary border highlight `#008774` paired with a soft 3px outer glow `rgba(0, 135, 116, 0.12)`.

## Shapes

The design system adopts a balanced **Rounded (`2`)** geometry:
- **Standard Cards & Containers:** Unified 16px corner radius (`1rem`), producing a friendly yet disciplined appearance.
- **Buttons:** 12px to 14px radius (`rounded-lg`) for primary action buttons, providing a clear pressable footprint.
- **Pills & Badges:** Fully rounded (`9999px`) for status pills (e.g., "Successful", "Granted", country-code selector).
- **Circular Elements:** 1:1 aspect ratio with full circular border-radius for transaction avatars, icon containers, and navigation badge counters.

## Components

### Buttons
- **Primary Action Button:** Full-width mobile CTA. Background `#008774` (hover/active `#007A68`), text `#FFFFFF`, height 52px, radius 12px. Accompanied by inline directional icons (e.g., right arrow).
- **Secondary / Ghost Button:** Transparent background, text `#008774`, border `1px solid #008774` or subtle `#E2E8F0`.
- **Destructive Button:** Background `#DC2626` or `#991B1B` with white text for critical reset or cancellation steps.

### Inputs & Phone Number Pickers
- **Compound Phone Input:** Split layout featuring an integrated country-code selector pill (`IN +91` with downward chevron) alongside a divider stroke and active phone number field.
- **Standard Input Field:** Minimum 48px height, rounded 10px corners, filled `#FFFFFF`, stroke `#E2E8F0`, active stroke `#008774`.

### Keypad Component
- **Structure:** 3x4 grid anchored at the bottom.
- **Key Dimensions:** Flat surface, full-width grid cells with active tap ripple. Each number features centered 24px digits with 10px uppercase sub-characters (e.g., `2` / `ABC`). Includes backspace and decimal/plus utility keys.

### Transaction Cards & Activity Rows
- **Layout:** Horizontal alignment containing:
  - Left: 40px circular initials badge or category icon (light slate `#F1F5F9` background, `#0F172A` text).
  - Center: Recipient/Merchant title in bold, timestamp and payment method label in muted body text.
  - Right: Bold transaction amount (e.g., `₹500`), with an emerald status chip or label (`Successful`).

### Bottom Navigation
- **Navigation Items:** Three primary destinations: **Home**, **History**, and **Settings**.
- **Visual State:** 56px height plus bottom home indicator offset. Active tab uses `#008774` for both icon and label text; inactive tabs use muted slate `#94A3B8`. Minimal hairline border on top edge (`#E2E8F0`).

### QR Scanner Frame
- **Camera Viewport:** Fullscreen dark backdrop with a centered transparent scanning target bounded by four emerald/white rounded corner-brackets (`#008774`). Includes quick-action pills for Flash and Upload below the reticle.