package com.hsib.sib.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.hsib.sib.data.Lang

val LocalLang = staticCompositionLocalOf { Lang.EN }

/**
 * The words of the screens in other languages. Each entry is the exact English text and its translation. Anything not listed
 * simply stays in English, so a missing line never breaks a screen. To add or fix a line, edit the tables below; nothing else
 * needs to change. The tables cover the main screens, buttons and settings, and grow with each version.
 */
object Strings {
    val bn: Map<String, String> = mapOf(
        // main tabs and headers
        "Today" to "আজ", "Plan" to "পরিকল্পনা", "Review" to "পর্যালোচনা", "Messages" to "বার্তা", "More" to "আরও", "Settings" to "সেটিংস",
        "All deadlines" to "সব সময়সীমা", "Friends" to "বন্ধু", "Guide" to "গাইড", "About" to "পরিচিতি", "Appearance" to "চেহারা",
        "Account" to "অ্যাকাউন্ট", "Calendar" to "ক্যালেন্ডার", "Reminders" to "অনুস্মারক", "Motivation" to "অনুপ্রেরণা", "Language" to "ভাষা",
        "Make it yours" to "নিজের মতো করুন", "Add" to "যোগ করুন", "Paste" to "পেস্ট", "Photo" to "ছবি", "Scan" to "স্ক্যান", "File" to "ফাইল", "Speak" to "বলুন",
        // buttons
        "Save" to "সংরক্ষণ", "Cancel" to "বাতিল", "Next" to "পরের", "Back" to "পেছনে", "Start" to "শুরু", "Done" to "সম্পন্ন", "Open" to "খুলুন", "Edit" to "সম্পাদনা",
        "Delete" to "মুছুন", "Remove" to "সরান", "Share" to "শেয়ার", "Search" to "খুঁজুন", "Accept" to "গ্রহণ", "Decline" to "প্রত্যাখ্যান", "Block" to "ব্লক",
        "Message" to "বার্তা পাঠান", "Confirm" to "নিশ্চিত", "Skip" to "বাদ দিন", "Later" to "পরে", "Details" to "বিস্তারিত", "Close" to "বন্ধ", "Got it" to "বুঝেছি",
        "Move" to "সরান", "Send" to "পাঠান", "Add a deadline" to "সময়সীমা যোগ করুন", "Add a step" to "ধাপ যোগ করুন", "Add one" to "একটি যোগ করুন",
        "Save name" to "নাম সংরক্ষণ", "Save username" to "ইউজারনেম সংরক্ষণ", "Use these steps" to "এই ধাপগুলো ব্যবহার করুন", "Write my own step" to "নিজের ধাপ লিখুন",
        "Start a focus session" to "মনোযোগ সেশন শুরু করুন", "Attach a file" to "ফাইল যুক্ত করুন", "Show me" to "আমাকে দেখান", "No thanks" to "না, ধন্যবাদ",
        // plan and deadlines
        "Week" to "সপ্তাহ", "Month" to "মাস", "Runway" to "রানওয়ে", "Load" to "চাপ", "Overview" to "সারসংক্ষেপ", "Steps" to "ধাপ", "History" to "ইতিহাস",
        "Also coming up" to "আরও আসছে", "Today's classes" to "আজকের ক্লাস", "Hours and importance" to "সময় ও গুরুত্ব", "Reminder days" to "অনুস্মারকের দিন",
        "Pause" to "বিরতি", "Checklist" to "চেকলিস্ট", "Files" to "ফাইল", "Focus session" to "মনোযোগ সেশন", "Suggested for you" to "আপনার জন্য প্রস্তাব",
        "Nothing is waiting for you" to "কিছুই অপেক্ষায় নেই", "Chat with friends and shared deadlines" to "বন্ধুদের সাথে ও ভাগ করা সময়সীমা নিয়ে আলাপ",
        "Find a friend" to "বন্ধু খুঁজুন", "Wants to chat" to "কথা বলতে চায়", "Join with a code" to "কোড দিয়ে যোগ দিন", "Username" to "ইউজারনেম",
        // settings
        "Daily use" to "দৈনন্দিন ব্যবহার", "Data and safety" to "তথ্য ও নিরাপত্তা", "Help" to "সাহায্য", "Backup and restore" to "ব্যাকআপ ও পুনরুদ্ধার",
        "Security" to "নিরাপত্তা", "Privacy and data" to "গোপনীয়তা ও তথ্য", "Send feedback" to "মতামত পাঠান", "Reminder health check" to "অনুস্মারক পরীক্ষা",
        "Your name and hints" to "আপনার নাম ও ইঙ্গিত", "Study tools" to "পড়ার সরঞ্জাম", "Close ones" to "প্রিয়জন", "Show or hide" to "দেখান বা লুকান",
        "Today style" to "আজকের ধরন", "Theme" to "থিম", "Motion" to "গতি", "Opening" to "শুরুর দৃশ্য", "Screen privacy" to "স্ক্রিনের গোপনীয়তা",
        "Easy mode" to "সহজ মোড", "How it works" to "কীভাবে কাজ করে", "Credits" to "কৃতজ্ঞতা", "Legal" to "আইনি",
        "Follow my phone" to "ফোন অনুযায়ী", "Light" to "হালকা", "Twilight" to "গোধূলি", "Black" to "কালো", "Neon" to "নিয়ন", "Cosmos" to "মহাকাশ", "Dune" to "মরুভূমি", "Forest" to "বন", "Ocean" to "সাগর",
        "Focus" to "ফোকাস", "Swipe" to "সোয়াইপ", "Stack" to "স্তূপ", "Collage" to "কোলাজ",
        // first run
        "Choose your language" to "আপনার ভাষা বেছে নিন", "You can change it later in Settings." to "পরে সেটিংসে বদলাতে পারবেন।",
        "What fits your days?" to "আপনার দিনের সাথে কোনটি মানায়?", "I study medicine" to "আমি চিকিৎসাবিজ্ঞান পড়ি", "I study at a university or college" to "আমি বিশ্ববিদ্যালয় বা কলেজে পড়ি",
        "I have a job or profession" to "আমার একটি চাকরি বা পেশা আছে", "I want it for daily life" to "আমি দৈনন্দিন জীবনের জন্য চাই", "None of these, keep it plain" to "এর কোনোটিই নয়, সাধারণ রাখুন",
        "How do you want the app?" to "অ্যাপটি কেমন চান?", "Easy: only the basics" to "সহজ: শুধু প্রয়োজনীয়টুকু", "Full: everything" to "পূর্ণ: সবকিছু",
        "What should we call you? (optional)" to "আপনাকে কী নামে ডাকব? (ঐচ্ছিক)", "Start before it is late." to "দেরি হওয়ার আগেই শুরু করুন।",
        "Needs more time" to "আরও সময় দরকার", "See options" to "বিকল্প দেখুন", "Hide options" to "বিকল্প লুকান", "Nothing found" to "কিছু পাওয়া যায়নি",
    )

    val hi: Map<String, String> = mapOf(
        "Today" to "आज", "Plan" to "योजना", "Review" to "समीक्षा", "Messages" to "संदेश", "More" to "और", "Settings" to "सेटिंग्स",
        "All deadlines" to "सभी समय-सीमाएँ", "Friends" to "दोस्त", "Guide" to "मार्गदर्शिका", "About" to "परिचय", "Appearance" to "रूप-रंग",
        "Account" to "खाता", "Calendar" to "कैलेंडर", "Reminders" to "अनुस्मारक", "Motivation" to "प्रेरणा", "Language" to "भाषा",
        "Make it yours" to "इसे अपना बनाएँ", "Add" to "जोड़ें", "Paste" to "चिपकाएँ", "Photo" to "फ़ोटो", "Scan" to "स्कैन", "File" to "फ़ाइल", "Speak" to "बोलें",
        "Save" to "सहेजें", "Cancel" to "रद्द करें", "Next" to "आगे", "Back" to "पीछे", "Start" to "शुरू करें", "Done" to "पूरा", "Open" to "खोलें", "Edit" to "बदलें",
        "Delete" to "हटाएँ", "Remove" to "निकालें", "Share" to "साझा करें", "Search" to "खोजें", "Accept" to "स्वीकार करें", "Decline" to "मना करें", "Block" to "ब्लॉक करें",
        "Message" to "संदेश भेजें", "Confirm" to "पक्का करें", "Skip" to "छोड़ें", "Later" to "बाद में", "Details" to "ब्योरा", "Close" to "बंद करें", "Got it" to "समझ गया",
        "Move" to "खिसकाएँ", "Send" to "भेजें", "Add a deadline" to "समय-सीमा जोड़ें", "Add a step" to "कदम जोड़ें", "Add one" to "एक जोड़ें",
        "Save name" to "नाम सहेजें", "Save username" to "यूज़रनेम सहेजें", "Use these steps" to "ये कदम इस्तेमाल करें", "Write my own step" to "अपना कदम लिखें",
        "Start a focus session" to "फ़ोकस सत्र शुरू करें", "Attach a file" to "फ़ाइल जोड़ें", "Show me" to "मुझे दिखाएँ", "No thanks" to "नहीं, धन्यवाद",
        "Week" to "सप्ताह", "Month" to "महीना", "Runway" to "रनवे", "Load" to "बोझ", "Overview" to "सारांश", "Steps" to "कदम", "History" to "इतिहास",
        "Also coming up" to "आगे और भी", "Today's classes" to "आज की कक्षाएँ", "Hours and importance" to "घंटे और अहमियत", "Reminder days" to "अनुस्मारक के दिन",
        "Pause" to "रोकें", "Checklist" to "चेकलिस्ट", "Files" to "फ़ाइलें", "Focus session" to "फ़ोकस सत्र", "Suggested for you" to "आपके लिए सुझाव",
        "Nothing is waiting for you" to "आपका कुछ भी इंतज़ार नहीं कर रहा", "Chat with friends and shared deadlines" to "दोस्तों और साझा समय-सीमाओं पर बातचीत",
        "Find a friend" to "दोस्त खोजें", "Wants to chat" to "बात करना चाहता है", "Join with a code" to "कोड से जुड़ें", "Username" to "यूज़रनेम",
        "Daily use" to "रोज़ का उपयोग", "Data and safety" to "डेटा और सुरक्षा", "Help" to "मदद", "Backup and restore" to "बैकअप और बहाली",
        "Security" to "सुरक्षा", "Privacy and data" to "गोपनीयता और डेटा", "Send feedback" to "राय भेजें", "Reminder health check" to "अनुस्मारक जाँच",
        "Your name and hints" to "आपका नाम और सुझाव", "Study tools" to "पढ़ाई के साधन", "Close ones" to "अपने लोग", "Show or hide" to "दिखाएँ या छिपाएँ",
        "Today style" to "आज का रूप", "Theme" to "थीम", "Motion" to "गति", "Opening" to "शुरुआती दृश्य", "Screen privacy" to "स्क्रीन की गोपनीयता",
        "Easy mode" to "आसान मोड", "How it works" to "यह कैसे काम करता है", "Credits" to "आभार", "Legal" to "कानूनी",
        "Follow my phone" to "फ़ोन के अनुसार", "Light" to "हल्का", "Twilight" to "गोधूलि", "Black" to "काला", "Neon" to "नियॉन", "Cosmos" to "ब्रह्मांड", "Dune" to "रेगिस्तान", "Forest" to "जंगल", "Ocean" to "सागर",
        "Focus" to "फ़ोकस", "Swipe" to "स्वाइप", "Stack" to "ढेर", "Collage" to "कोलाज",
        "Choose your language" to "अपनी भाषा चुनें", "You can change it later in Settings." to "आप इसे बाद में सेटिंग्स में बदल सकते हैं।",
        "What fits your days?" to "आपके दिनों के लिए क्या ठीक रहेगा?", "I study medicine" to "मैं चिकित्सा की पढ़ाई करता/करती हूँ", "I study at a university or college" to "मैं विश्वविद्यालय या कॉलेज में पढ़ता/पढ़ती हूँ",
        "I have a job or profession" to "मेरी नौकरी या पेशा है", "I want it for daily life" to "मुझे यह रोज़मर्रा की ज़िंदगी के लिए चाहिए", "None of these, keep it plain" to "इनमें से कोई नहीं, सादा रखें",
        "How do you want the app?" to "आप ऐप कैसा चाहते हैं?", "Easy: only the basics" to "आसान: सिर्फ़ ज़रूरी चीज़ें", "Full: everything" to "पूरा: सब कुछ",
        "What should we call you? (optional)" to "हम आपको क्या कहकर बुलाएँ? (वैकल्पिक)", "Start before it is late." to "देर होने से पहले शुरू करें।",
        "Needs more time" to "और समय चाहिए", "See options" to "विकल्प देखें", "Hide options" to "विकल्प छिपाएँ", "Nothing found" to "कुछ नहीं मिला",
    )
}

/** The text in the language the person chose, or the English text when there is no translation for it. */
@Composable
fun tr(en: String): String = when (LocalLang.current) {
    Lang.EN -> en
    Lang.BN -> Strings.bn[en] ?: en
    Lang.HI -> Strings.hi[en] ?: en
}
