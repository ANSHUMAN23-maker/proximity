export interface FaqEntry {
  question: string;
  answer: string;
}

export interface RailSpec {
  id: number;
  label: string;
  eyebrow: string;
  title: string;
  description: string;
  spec: string;
  iconSvg: string;
}

export const RAIL_SPECS: RailSpec[] = [
  {
    id: 0,
    label: '123Pay Voice',
    eyebrow: '01 \u00b7 VOICE IVR \u00b7 08045163666',
    title: 'UPI 123Pay Voice Call',
    description:
      "Dials NPCI's official 08045163666 IVR hotline and sends DTMF tones with recipient phone and whole-rupee value. Works on Jio, Airtel, Vi, and BSNL from \u20b91 to \u20b94,999.",
    spec: 'tel:08045163666,,1,<phone>,,<value>,,1',
    iconSvg:
      '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><path fill-rule="evenodd" d="M10.6925 4.95063C9.52266 2.85451 6.68752 2.72679 5.00745 4.40686C4.10879 5.30552 3.3103 6.50044 3.25706 7.90463C3.17818 9.98556 3.71556 13.4408 7.13735 16.8626C10.5591 20.2844 14.0144 20.8218 16.0953 20.7429C17.4995 20.6896 18.6944 19.8911 19.5931 18.9925C21.2731 17.3124 21.1454 14.4773 19.0493 13.3075L17.8864 12.6584C16.5176 11.8945 14.7905 12.2201 13.6585 13.3383C13.6381 13.3533 13.5231 13.4322 13.3221 13.442C13.0656 13.4545 12.4729 13.3632 11.5548 12.4451C10.6364 11.5267 10.5454 10.9339 10.5579 10.6776C10.5677 10.4768 10.6467 10.3618 10.6616 10.3414C11.7799 9.20946 12.1054 7.48238 11.3415 6.11356L10.6925 4.95063Z"></path><path d="M18 9.75C18.41 9.75 18.75 9.41 18.75 9C18.75 8.59 18.41 8.25 18 8.25H16.81L19.53 5.53C19.82 5.24 19.82 4.76 19.53 4.47C19.24 4.18 18.76 4.18 18.47 4.47L15.75 7.19V6C15.75 5.59 15.41 5.25 15 5.25C14.59 5.25 14.25 5.59 14.25 6V9C14.25 9.41 14.59 9.75 15 9.75H18Z"></path></svg>'
  },
  {
    id: 1,
    label: '*99# USSD',
    eyebrow: '02 \u00b7 SHORTCODE STREAM \u00b7 *99*1*1#',
    title: 'Automated *99# USSD QR',
    description:
      "Scans standard merchant UPI QRs offline and drives Android's carrier *99# menu via a user-enabled accessibility helper throttled at 150ms with a strict 30s auto-disable guard.",
    spec: 'Airtel \u00b7 Vi \u00b7 BSNL \u00b7 Up to \u20b9100,000',
    iconSvg:
      '<svg viewBox="0 0 24 24" width="20" height="20" fill="currentColor" aria-hidden="true"><path d="M5 5h2.5v2.5H5zM16.5 5H19v2.5h-2.5zM5 16.5h2.5V19H5zM13 13h3v3h-3zM17 17h4v4h-4z"></path><path fill-rule="evenodd" d="M2 5.5C2 3.57 3.57 2 5.5 2H8.5C10.43 2 12 3.57 12 5.5V8.5C12 10.43 10.43 12 8.5 12H5.5C3.57 12 2 10.43 2 8.5V5.5ZM5.5 3.5C4.4 3.5 3.5 4.4 3.5 5.5V8.5C3.5 9.6 4.4 10.5 5.5 10.5H8.5C9.6 10.5 10.5 9.6 10.5 8.5V5.5C10.5 4.4 9.6 3.5 8.5 3.5H5.5ZM12 5.5C12 3.57 13.57 2 15.5 2H18.5C20.43 2 22 3.57 22 5.5V8.5C22 10.43 20.43 12 18.5 12H15.5C13.57 12 12 10.43 12 8.5V5.5ZM15.5 3.5C14.4 3.5 13.5 4.4 13.5 5.5V8.5C13.5 9.6 14.4 10.5 15.5 10.5H18.5C19.6 10.5 20.5 9.6 20.5 8.5V5.5C20.5 4.4 19.6 3.5 18.5 3.5H15.5ZM2 15.5C2 13.57 3.57 12 5.5 12H8.5C10.43 12 12 13.57 12 15.5V18.5C12 20.43 10.43 22 8.5 22H5.5C3.57 22 2 20.43 2 18.5V15.5ZM5.5 13.5C4.4 13.5 3.5 14.4 3.5 15.5V18.5C3.5 19.6 4.4 20.5 5.5 20.5H8.5C9.6 20.5 10.5 19.6 10.5 18.5V15.5C10.5 14.4 9.6 13.5 8.5 13.5H5.5Z"></path></svg>'
  },
  {
    id: 2,
    label: 'Walled Garden',
    eyebrow: '03 \u00b7 ZERO-BALANCE DETECTOR \u00b7 HTTP 204 PROBE',
    title: 'Cellular Walled Garden',
    description:
      'Binds directly to TRANSPORT_CELLULAR and probes HTTP 204 connectivity checks to spot carrier-whitelisted recharge portals, letting you buy mobile packs even with zero data balance.',
    spec: 'generate_204 \u00b7 Cellular Transport Only',
    iconSvg:
      '<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17 15V17.8C17 19.9 15.9 21 13.8 21H8.2C6.1 21 5 19.9 5 17.8V5.6C5 3.8 5.8 3 7.6 3H11M10 18H12"></path><path d="M14 4H19M14 6.5H19M16.5 6.5C18 6.5 18 10 14.8 10L18.5 13" stroke-width="1.8"></path></svg>'
  }
];

export const FAQ_ENTRIES: FaqEntry[] = [
  {
    question: 'What is Proximity?',
    answer:
      "Proximity is a payment application that helps you pay when your mobile data is weak or has run out. Instead of using the internet, it uses your phone's standard cellular call network."
  },
  {
    question: 'Does Proximity work without the internet?',
    answer:
      'Yes. Proximity works without mobile data. However, your phone still needs a network signal and an active recharge plan, because the payment is routed through a voice call or a USSD code (a short code dialled on your phone).'
  },
  {
    question: 'What is UPI123Pay?',
    answer:
      "UPI123Pay is NPCI's offline UPI service, where you pay through a voice call (IVR). You call the service, follow the spoken prompts, and confirm the payment with your UPI PIN."
  },
  {
    question: 'What is USSD *99#?',
    answer:
      "USSD (*99#) is a short code dialled on your phone that opens the NPCI UPI menu. Proximity can start this for you, so you don't have to type the code yourself. It works on any phone with a signal and doesn't need a smartphone data connection."
  },
  {
    question: 'Is Proximity a bank or a wallet?',
    answer:
      'No. Proximity is solely a payment aggregator. It does not hold your money or manage your bank account. Your money is always transferred from your bank through official channels.'
  },
  {
    question: 'Which banks does Proximity support?',
    answer:
      'Proximity works with any bank that is linked to UPI (Unified Payments Interface) and supports UPI123 or *99#.'
  },
  {
    question: 'Which mobile number should I use?',
    answer: 'Use the same mobile number that is linked to your bank account and UPI.'
  },
  {
    question: 'How do I pay someone?',
    answer:
      "To pay someone:\n1. Tap Pay a number.\n2. Enter the recipient's mobile number and the amount.\n3. Review the payment details.\n4. Tap Pay.\nProximity then places an IVR (Interactive Voice Response) call to process the payment."
  },
  {
    question: 'How do I pay a shop or merchant using a QR code?',
    answer:
      "To pay by scanning the shop's QR code:\n1. Tap Scan QR code.\n2. Point your camera at the shop's QR code.\n3. Your phone opens a bank pop-up. Enter your UPI PIN there.\n4. Return to Proximity and tap Finish Payment."
  },
  {
    question: 'Why does a call open when I make a payment?',
    answer:
      'Proximity uses the official UPI123 phone service. The call is the channel through which your payment is transmitted when an internet connection is not available.'
  },
  {
    question: 'Is it safe to enter my UPI PIN?',
    answer:
      'Your UPI PIN is entered only in the official bank pop-up or call. Proximity never reads or stores your UPI PIN.'
  },
  {
    question: 'What information does Proximity save?',
    answer:
      'Proximity saves only your UPI-linked mobile number and your payment history on your own phone. It never requests your bank account number or IFSC code.'
  },
  {
    question: 'Why does the app ask for permission to read my SMS messages?',
    answer:
      'This permission allows Proximity to check the SMS sent by your bank and confirm that your payment was completed successfully.'
  },
  {
    question: 'What if my payment fails?',
    answer:
      'Please don\'t worry.\n- First, check the SMS from your bank to see whether the amount was debited.\n- If the amount was not debited, simply try the payment again.\n- If the amount was debited but the app shows "Failed", wait for some time, as banks usually return the amount within a few days. If it is still not returned, contact your bank with the transaction details.'
  },
  {
    question: 'Are there payment limits?',
    answer:
      'Yes. Payment limits are set by UPI and your bank, and they may be lower for call-based payments. Please check with your bank for the current limit.'
  },
  {
    question: 'Does Proximity charge any fee?',
    answer:
      'Proximity itself does not charge you a fee. However, your bank or mobile service provider may charge a small fee for calls or USSD, so please check your plan.'
  }
];
