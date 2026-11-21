# AIRA - Personal Android Assistant

## Features Included
- **Offline Local Command Router:** Flashlight on/off, Volume up/down/full/silent, Battery percentage, Wi-Fi/Bluetooth/Settings shortcuts, Camera, Alarms, YouTube search, Installed Apps launch.
- **Triggers:** In-app Mic button, Text command fallback, Floating Bubble overlay (`FloatingBubbleService`), Quick Settings Tile (`AiraTileService`).
- **Screen Automation (`AiraAccessibilityService`):** Scroll up/down, Back, Home, aur screen par kisi text par tap (`click <text>`).
- **Online AI Fallback (`AiBrainClient`):** OpenAI/Groq-compatible endpoint hook (bas `API_KEY` set karein).
- **GitHub Actions Workflow:** `.github/workflows/build.yml` se seedha Debug APK build hota hai.

## Realme C3 (Android 11) Setup Tips
1. App install karne ke baad **App Info > Battery usage** mein **Allow auto-launch** aur **Allow background activity** on karein.
2. Floating Bubble ke liye **Display over other apps** permission allow karein.
3. Screen scroll aur click commands ke liye **Settings > Accessibility > AIRA Screen Control** on karein.
