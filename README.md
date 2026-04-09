[<img width="1500" height="429" alt="SpeakThat! Header" src="https://github.com/user-attachments/assets/f7acb89c-9c81-406f-9e7f-dad3e01ffce0" />](https://speakthat.app)

---

[<img width="1500" height="153" alt="Frame 6327" src="https://github.com/user-attachments/assets/f333b8b0-fe29-481e-b24a-df845e22cc0e" />
](https://github.com/mitchib1440/SpeakThat/wiki)

SpeakThat (styled as “**_SpeakThat!_**”) is an open-source Android application that reads incoming notifications out loud using Android’s text-to-speech (TTS) engine. It was designed to continue the legacy of older notification reading applications, mainly Touchless Notifications developed by [DYNA Logix](https://dynalogix.eu/) in the mid-2010s. SpeakThat differs from other notification readers in its customization and user interface. Some notifications contain information that you would rather not be made public. SpeakThat allows you to control exactly what gets read out, and when. You can decide what apps get their notifications read out, set up words that prevent readouts, enable private mode for certain notifications, add cooldown periods to prevent spam, and just about anything else you could want. While plenty of notification readers feature adverts or in-app purchases, SpeakThat offers all of its features for free. No ads, no paying for extra readouts, and absolutely no subscriptions. SpeakThat is open-source under the GPL-3.0 license, giving you permission to use it, modify it, and share it provided any derivatives are under the same license and its original copyright holder is attributed.

[![IzzyOnDroid](https://img.shields.io/badge/IzzyOnDroid-Available-green?logo=android)](https://apt.izzysoft.de/fdroid/index/apk/com.micoyc.speakthat)
[<img src="https://img.shields.io/badge/SpeakThat!-Google_Play-blue?logo=googleplay" alt="RB shield">](https://play.google.com/store/apps/details?id=com.micoyc.speakthat)
[![License](https://img.shields.io/badge/License-GPL--3.0-green.svg)](LICENSE)
[<img src="https://shields.rbtlog.dev/simple/com.micoyc.speakthat" alt="RB shield">](https://shields.rbtlog.dev/com.micoyc.speakthat)
[![Weblate](https://hosted.weblate.org/widgets/speakthat/-/svg-badge.svg)](https://speakthat.app/translate)
[<img src="https://img.shields.io/badge/SpeakThat!-Official_Website-purple" alt="RB shield">](https://speakthat.app)


## The Journey to get here!

I have been using notification readers for almost as long as I have used smartphones. On my first proper smartphone, I had been using Utter!, created by Ben Randall. It had a notification reader function and while it didn't offer as many options as modern readers do, it showed me how great it was to have a phone read notifications. My eyes felt more free, not being forced to look at what was a very small screen to see very small announcements.

Utter! soon got replaced by Saiy, which was a ground-up re-write and open source to boot! The issue with both of these, however, was that they were fundamentally NOT notification readers. It was just a neat feature they had. And as it wasn't the primary focus of the app, they didn't get many new features. And out of curiousity I decided to see what was available if I searched for dedicated notification reader apps.

That's when I came across Touchless Notifications, created by DYNA Logix. This was everything I had wanted and more. It had genuinely useful features, such as shake-to-stop, word filters, pronunciation correction, and a comprehensive triggers system similar to what is possible with MacroDroid or Tasker. I used Touchless Notifications, specifically the Pro version, very happily for many years. I noticed, however, that some time in 2019 it appeared to have been removed from the Play Store. I asked the creator what had happened and he explained to me that the app had been unfairly removed from the Play Store from Google for having two different app shortcuts. And despite this being a rule that didn't seem to be enforced for anyone else, and potentially didn't even exist, DYNA Logix decided to not fight it as they had other apps on the Play Store and didn't want to risk angering Google. They recommended that I download an APK extractor and save the package file for Touchless Notifications Pro so I could continue to use it on my future devices. And that's what I did for the next 6 years.

Fast forward to 2025 and it's quite clear that Touchless Notifications was designed for an old version of Android. Unlike Utter!, it still works okay. But I had a feeling that may not be for long. And modern Android had introduced some annoying bugs that definitely weren't going to be fixed over half a decade after the final update was released.
So, I decided to make my own. I had this idea for a while but coding is not an easy thing to get into. But with the help of AI-assisted coding, it becomes a very feasible dream.
And so after a few failed versions, I'm pleased to introduce SpeakThat! The notification reader making use of my now 12+ years of user testing in this field!

---

<img width="3618" height="1245" alt="Frame 6331" src="https://github.com/user-attachments/assets/d25c3f44-e9b4-4c0d-b613-a7ddad144f0e" />


## Features

* **Privacy-First By Design**
  * Absolutely nothing leaves your device unless you explicitly export a debug log.
  * All text processing happens locally. You have 100% control over what SpeakThat! can see and read.

* **Unmatched Notification Filtering**
  * **Notification History:** View recent notifications and create custom filters on the fly.
  * **Smart Patterns:** Automatically strip out changing variables (like `[NUMBER]` or `[TIME]`) from progress bars and dynamic alerts so they don't spam you.
  * **Word Swaps & Blacklists:** Block specific phrases entirely, or substitute words with custom pronunciation alternatives.
  * **App Management:** Precisely whitelist or blacklist individual apps.

* **Smart Rules & External Automation**
  * **Conditional Rules Engine:** Built-in automation and full logic that allows you to control exactly *when* SpeakThat reads. Trigger readouts based on connected Bluetooth devices, specific Wi-Fi networks, time of day, or the current foreground app, and many more.
  * **External Automation Support:** Seamlessly integrate with Tasker or MacroDroid using standard Android Broadcast Intents for unprecedented control.

* **Private Mode**
  * Keep sensitive information safe. When an app or filter is flagged for Private Mode, SpeakThat! will announce the app's name out loud, but will keep the actual message content completely hidden.

* **Smart Audio & Gesture Controls**
  * **Shake to Stop:** Instantly silence a readout by shaking your device (with adjustable sensitivity).
  * **Wave to Stop:** Wave your hand over your device's proximity sensor to instantly kill the audio.
  * **Behaviour Tweaks:** Utilize Audio Ducking (lowers background music during readouts), set readout delays, and enable cooldowns to prevent notification spam from active group chats.

* **Accessible, Customizable, & Debuggable**
  * Clean, intuitive Material Design UI with a Quick Settings tile for easy lockscreen access.
  * Adjust speech rate, pitch, and select preferred external TTS engines.
  * **SelfTest Diagnostics:** A built-in diagnostic tool that outputs specific 4-digit error codes to help pinpoint exactly why a notification wasn't read.
  * Export and import your configuration to effortlessly switch devices.

* **No Ads, No Tracking, Just Speech**
  * Fully open-source under the GPL-3.0 License for total transparency.
  * Free to download here on GitHub (includes a built-in self-updater!).
  * Available on the Google Play Store, Droid-ify, Neo-Store, or anywhere else supporting the [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/com.micoyc.speakthat) repo!

---

## Getting Started

1. Download the latest APK from the Releases tab and save it to your phone. *(Note: Different editions are available for F-Droid and Google Play depending on your preference).*
2. Tap the APK to install it.
3. Open SpeakThat! and follow the Smart Onboarding process.
4. **Important Permission Step:** SpeakThat requires **Notification Access** to function.

---

## Privacy & Security

Notification readers are fundamentally a privacy risk because they take information on your phone and speak it loudly to anybody nearby. SpeakThat! was built to solve this:

* Your notifications and data **never** leave your device. All text-to-speech processing is entirely local.
* SpeakThat! features a built-in event logging system to help diagnose bugs. Up to 500 log entries are saved purely on your local device.
* If you submit a bug report, you can review and redact any diagnostic data or logs before sending the email.

---

## Feedback & Support

* Found a bug? Have a feature request? [Open an issue](https://github.com/mitchib1440/SpeakThat/issues).
* For support, use the in-app **"Support & Feedback"** option. This will automatically draft an email with helpful diagnostic data (which you can review/redact) so I can better assist you.
* **Please Note:** SpeakThat! is entirely free, and support is provided voluntarily. Please ensure all interactions remain respectful and constructive!

---

## Acknowledgements

As I mentioned in the overview, SpeakThat was mostly inspired by Touchless Notifications by [DYNA Logix](https://dynalogix.eu/). Without his original app, I may have never fallen in love with notification readers and SpeakThat would simply not exist. Thank you ever so much for creating something truly ahead of its time. And I’m sorry it received such unfair punishment from Google!

SpeakThat uses many icons from the [Material Icons](https://fonts.google.com/icons) library. These are available under the Apache 2.0 license, and make SpeakThat look much more professional than it has any right to be! So a huge thank you to the Material design teams.

I should also thank the people making improvements in AI-assisted programming. SpeakThat simply couldn’t exist in its current form without this technology. I’ve personally been using [Cursor](https://cursor.com/) for my use. It's far from flawless and we've nearly fallen out several times, but they have improved their software massively since I started using it.

Thanks also to the [Android](https://www.android.com/) development teams for making an operating system capable of working for everyone. Your work is greatly underappreciated!

Special thanks to my Mum, who supported the project from the very beginning and was willing to help me test it by trialling it on her phone! Thanks, Rusty!

I’d also like to thank my work colleagues, especially Connagh, who convinced me to try AI-assisted programming as I was very hesitant at first!

As this is the first time I had ever attempted anything like this, I ended up checking some of my basic notification reading and audio processing code to that of [Voice Notify](https://voicenotify.app/), created by [Pilot51](https://pilot51.com/wiki/Main_Page). I didn’t take any code directly, but since a lot of users were telling me certain things were working with Voice Notify but not with SpeakThat, its publicly-accessible code was incredibly helpful in making sure I wasn’t taking the wrong path. So massive thanks to Pilot51.

On the subject of open source, I can't _not_ mention [Izzy](https://www.izzysoft.de/) from the [IzzyOnDroid repository](https://android.izzysoft.de/intro.php), who quite frankly deserves a gold medal and lifetime-supply of free drinks for his patience with me. Not only has he provided [thousands more users access to SpeakThat](https://dlstats.izzyondroid.org/#app-trends?app=com.micoyc.speakthat&from=2025-07-01&to=2026-03-05&clients=_total&top=50&type=F-Droid+Classic&agg=monthly), he makes sure everything is done as ethically and respectfully as possible, **and** he constantly helps clueless fools like me getting started with F/LOSS. I have no idea how you do it, but more power to you, good sir!

Thanks to [HowToMen](https://www.youtube.com/@howtomen) for [featuring SpeakThat](https://youtu.be/iwvHk4SUrMQ?si=DVL6sAUJEvglgTdu&t=222) on his YouTube channel!

And of course, thank you to all of the contributors, of which there are far too many to list at this point. But whether you contributed translations, code, donations, or even just constructive criticism, thank you so much. You shaped SpeakThat into something that truly helps people.

---

**Enjoy using SpeakThat! Stay safe, stay connected, and let your phone do the talking!**

---

## SpeakThat! & AI Usage

As the world heavily pivots towards artificial intelligence, I believe transparency with how work was created is more important than ever before.

SpeakThat’s code heavily makes use of AI-assisted programming. While I do my best to vet code used in the project and of course test new versions of the app on both my own devices as well as emulated devices before any of it goes public, there always exists the possibility for errors and faults to slip through into release. As you should expect, I take full responsibility for these mistakes and ask for your understanding as I work through them. I encourage the ethical use of AI, and any code contributions made using AI must be reviewed by a competent human with some understanding of the programming language.

I should state, however, that absolutely zero graphical work used by/for SpeakThat was generated by artificial intelligence. All visuals (Including logos, banners, and header images) are my own work, created using manual tools in Figma.

SpeakThat's external documentation was also written by myself, using LibreOffice Writer. That said, I have used artificial intelligence to refine my wording in some areas for better clarity (and because I’m not all that great with technical explanations).

I greatly value the human experience of creation and thus only use AI as assistive tool, making sure my usage is both ethical and transparent.

---

## Legalities

SpeakThat! is free and open-source software, released under the GNU GPL v3.0, a copyleft license that ensures modified and redistributed versions remain free and properly attributed.

This license allows you to download, modify, and redistribute SpeakThat, provided that any redistributed or modified versions remain under the same license and retain the original copyright notices.

- SpeakThat! Copyright © Mitchell Bell
- SPEAKTHAT is a registered trademark of Mitchell Bell 

---

## Star History

<a href="https://www.star-history.com/#mitchib1440/speakthat&type=date&legend=top-left">
 <picture>
   <source media="(prefers-color-scheme: dark)" srcset="https://api.star-history.com/svg?repos=mitchib1440/speakthat&type=date&theme=dark&legend=top-left" />
   <source media="(prefers-color-scheme: light)" srcset="https://api.star-history.com/svg?repos=mitchib1440/speakthat&type=date&legend=top-left" />
   <img alt="Star History Chart" src="https://api.star-history.com/svg?repos=mitchib1440/speakthat&type=date&legend=top-left" />
 </picture>
</a>
