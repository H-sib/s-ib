"""Films the raw clips for the promo and tutorial videos on the phone (see STUDIO_HANDOFF.txt, VIDEOS REMADE).
Usage: python tools/video_shoots.py <take> [<take> ...]      a take is a name in TAKES below; each one is a list of phone_drive.py steps
       python tools/video_shoots.py list
Each take starts a screen recording (rec:), runs its steps and pulls store/videos/raw/<take>.mp4 and <take>.taps.json (endrec).
The raw folder is never published. Steps are explained at the top of phone_drive.py. The phone must be unlocked with the Workshop version 1.6 installed."""
import pathlib
import sys

sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent))
import phone_drive as drive

HUB = ["kill", "launch", "wait:6"]          # a cold start: the opening animation, then the hub

TAKES = {
    # the editor on the Neon design (a draft called Neon Night made by Sam Rahman is open): one tap looks, drag, whole page looks, Play
    "s_designer": [
        "rec:s_designer:110", "wait:1.5",
        "tap:Canvas layer title", "wait:0.8", "tap:Selected: Looks", "wait:1.2",
        "tapx:Look: Neon text", "wait:1.3", "tapx:Look: Highlighter", "wait:1.3", "tapx:Look: Sunset gradient", "wait:1.3", "tapx:Look: Big title", "wait:1.5",
        "tapx:Done", "xy:30,1500", "wait:1",
        "tap:Canvas layer orb", "wait:1", "swipe:801,1258,420,1500,900", "wait:1", "swipe:420,1500,760,1050,900", "wait:1.2",
        "xy:30,1500", "wait:0.8", "tapx:Backdrop", "wait:1.2",
        "tapx:Page look: Midnight", "wait:1.6", "tapx:Page look: Paper", "wait:1.6", "tapx:Page look: Deep space", "wait:1.6", "tapx:Done", "wait:1",
        "tapx:Play", "wait:2", "tap:Get started", "wait:1.5", "tap:Learn more", "wait:1.5", "endrec",
    ],
    # Expense tracker played in the Workshop (a draft Spend Smart is open in Play, on its Categories page): four expenses, then a category is renamed
    "s_expenses": [
        "rec:s_expenses:170", "wait:1", "tapx:Back#1", "wait:1.2", "tapx:Back#1", "wait:1.5", "tapx:Add expense", "wait:1",
        "set:Amount=4.20", "tapx:Transport", "tapx:Choose a date", "wait:1", "tapx:28", "tapx:OK", "set:Note=Bus", "wait:0.5",
        "tapx:Save", "wait:2", "tapx:Add expense", "wait:1", "set:Amount=38", "tapx:Bills", "tapx:Choose a date", "wait:1",
        "tapx:26", "tapx:OK", "set:Note=Wifi", "wait:0.5", "tapx:Save", "wait:2", "tapx:Add expense", "wait:1",
        "set:Amount=9.75", "tapx:Fun", "tapx:Choose a date", "wait:1", "tapx:29", "tapx:OK", "set:Note=Movie night", "wait:0.5",
        "tapx:Save", "wait:2", "wait:1.5", "tapx:Categories", "wait:1.5", "tapx:Fun", "wait:1.5", "tapx:Rename",
        "wait:1.2", "set:Name=Movies", "tapx:Save", "wait:2", "tapx:Back#1", "wait:1.2", "tapx:Back#1", "wait:1.8",
        "endrec",
    ],
    # Spend Smart open in Play on its home page with four expenses: the Categories page, a rename, and the old expenses follow
    "s_categories": [
        "rec:s_categories:70", "wait:1.2", "swipe:540,1900,540,1000,500", "wait:1", "tapx:Categories", "wait:1.6", "tapx:Fun", "wait:1.6", "tapx:Rename", "wait:1.4",
        "set:Name=Movies", "wait:0.6", "tapx:Save", "wait:2.2", "tapx:Back#1", "wait:1.4", "tapx:Back#1", "wait:1.8", "swipe:540,1700,540,900,500", "wait:2.5", "endrec",
    ],
    # Spend Smart open in the editor (design mode): Make a real app, and the credit line on the made screen
    "s_make": ["rec:s_make:50", "wait:1.2", "tapx:Make", "wait:6", "tapx:Open details", "wait:3", "endrec"],
    # the App settings sheet: the credit line is fixed
    "s_credit": ["rec:s_credit:30", "wait:1", "tap:App settings", "wait:2.5", "tapx:Done", "wait:1", "endrec"],
    # a drawing selected in the editor with its Code panel open (any draft that has a Drawing): the code editor, live examples
    "s_code": [
        "rec:s_code:70", "wait:1", "tap:Canvas layer canvas_2", "wait:0.8", "tapx:Code", "wait:1.2", "tap:Edit code", "wait:3", "chip:Golden spiral", "wait:4", "chip:Bouncing balls", "wait:4", "chip:Rose curve", "wait:4", "chip:Waves", "wait:4",
        "tapx:Done", "wait:1.5", "endrec",
    ],
    # the ready made Calculator app played in the Workshop (Play on, display 0); keys with signs are tapped by place: divide 634,692 times 824,692
    "s_calc": [
        "rec:s_calc:50", "wait:1", "tapx:7", "tapx:8", "xy:824,692", "tapx:6", "tapx:=", "wait:1.6", "tapx:C", "tapx:9", "tapx:9", "xy:634,692", "tapx:3", "tapx:=", "wait:1.6",
        "tapx:C", "tapx:1", "tapx:2", "tapx:5", "tapx:+", "tapx:7", "tapx:5", "tapx:=", "wait:2", "endrec",
    ],
    # the ready made Waiting line app in Play on its Add a person page: three people join, then they are called one by one
    "s_queue": [
        "rec:s_queue:70", "wait:1", "set:Name=Maya", "set:How many people=2", "wait:0.4", "tapx:Add to the line", "wait:1.8", "tapx:Add a person", "wait:1.2", "set:Name=Omar", "set:How many people=1", "wait:0.4", "tapx:Add to the line", "wait:1.8", "tapx:Add a person", "wait:1.2", "set:Name=Lena", "set:How many people=4", "wait:0.4", "tapx:Add to the line", "wait:1.8",
        "wait:1.5", "tapx:Call next", "wait:2", "tapx:Call next", "wait:2", "tapx:Call next", "wait:2.5", "endrec",
    ],
    # Catalog app from the hub: a loud page colour, then the Data tool opens (already filmed; a draft Bite Street exists, delete it before filming again)
    "s_street_a": [
        "rec:s_street_a:90", "wait:0.5", "tohub", "tapx:New app", "wait:1", "tapx:Catalog", "tapx:Next", "wait:1",
        "xy:540,660", "type:Bite Street", "xy:540,835", "type:Sam Rahman", "tapx:Next", "wait:1.2", "tapx:Customize first", "wait:3",
        "tapx:Backdrop", "wait:1.2", "swipe:540,2150,540,1850,300", "wait:0.8", "tapx:Page background: Warning", "wait:1.5", "tapx:Done", "wait:1",
        "tapx:Data", "wait:1.2", "swipe:540,2100,540,1700,300", "swipe:540,2100,540,1700,300", "wait:0.8", "endrec",
    ],
    # Bite Street in the Data tool: edit the first two records (price digits are typed backwards because the number field reformats as it goes)
    "s_street_b": [
        "rec:s_street_b:170", "wait:0.5", "tapx:Record 1", "wait:1.2", "swipe:540,2150,540,1750,300", "wait:0.6", "set:Name=Chotpoti", "enter",
        "swipe:540,2150,540,1750,300", "set:Price=06", "enter", "swipe:540,2150,540,1750,300", "set:Details=Hot and tangy with egg", "enter", "swipe:540,2150,540,1750,300", "tapx:Yes",
        "wait:0.8", "tapx:Record 2", "wait:1.2", "swipe:540,2150,540,1750,300", "wait:0.6", "set:Name=Fuchka", "enter", "swipe:540,2150,540,1750,300",
        "set:Price=04", "enter", "swipe:540,2150,540,1750,300", "set:Details=Crunchy shells spicy water", "enter", "swipe:540,2150,540,1750,300", "tapx:No", "wait:0.8",
        "endrec",
    ],
    # Bite Street, Backdrop sheet open on Custom colour: colours, two more records, Play and the Offers tab
    "s_street_c": [
        "rec:s_street_c:175", "wait:0.5", "set:Hex colour=#FF4B2B", "enter", "wait:1", "set:Hex colour=#2BD67B", "enter", "wait:1",
        "set:Hex colour=#FFC400", "enter", "wait:1", "tapx:Done", "wait:0.8", "tapx:Data", "wait:1.2", "tapr:Record 2",
        "wait:1.2", "set:Name=Fuchka", "enter", "set:Price=04", "enter", "set:Details=Crunchy shells spicy water", "enter", "wait:0.8",
        "tapr:Add record", "wait:1.5", "set:Name=Jhalmuri", "enter", "set:Price=03", "enter", "set:Details=Puffed rice mustard oil", "enter",
        "tapr:Yes", "wait:0.8", "tapx:Done", "wait:0.8", "tapx:Play", "wait:2.5", "tapx:Offers", "wait:2",
        "tapx:Shop", "wait:2", "endrec",
    ],
    # Bite Street with the Data sheet open on the last record: it goes on sale, then Play and the Offers tab
    "s_street_d": ["rec:s_street_d:60", "wait:0.5", "tapx:No", "wait:1.5", "tapx:Done", "wait:1", "tapx:Play", "wait:2.5", "tapx:Offers", "wait:2.5", "tapx:Shop", "wait:2", "endrec"],
    # Tutorial B, street food menu board, part one: from the hub a Playful design, the title and the three buttons get their words and their messages (Light look)
    "s_menu_a": [
        "rec:s_menu_a:178", "wait:0.5", "tohub", "tapx:New app", "wait:1", "tapx:Blank app", "tapx:Next", "wait:1.2",
        "tapx:Design: Playful", "wait:0.8", "tapx:Next", "wait:1", "xy:540,660", "type:Bite Street", "xy:540,835", "type:Sam Rahman",
        "tapx:Next", "wait:1.2", "tapx:Light", "tapx:Start designing", "wait:3", "tap:Canvas layer title", "wait:1", "tapx:Text",
        "wait:1.2", "set:Text=Menu", "enter", "tapx:Done", "wait:1.5", "xy:30,1500", "wait:1", "tap:Canvas layer play",
        "wait:1.2", "tapx:Text", "wait:1.2", "set:Label=Chotpoti 60", "enter", "tapx:Done", "wait:1.2", "tapx:Tap",
        "wait:1.2", "set:Message=Hot chotpoti coming up", "enter", "tapx:Done", "wait:1.2", "xy:30,1500", "wait:1", "tap:Canvas layer hello",
        "wait:1.2", "tapx:Text", "wait:1.2", "set:Label=Fuchka 40", "enter", "tapx:Done", "wait:1.2", "tapx:Tap",
        "wait:1.2", "set:Message=Crunchy fuchka for you", "enter", "tapx:Done", "wait:1.2", "xy:30,1500", "wait:1", "tap:Canvas layer learn",
        "wait:1.2", "tapx:Text", "wait:1.2", "set:Label=Jhalmuri 30", "enter", "tapx:Done", "wait:1.2", "tapx:Tap",
        "wait:1.2", "set:Message=Jhalmuri with extra mustard oil", "enter", "tapx:Done", "wait:1.2", "xy:30,1500", "wait:1", "endrec",
    ],
    # Tutorial B part two (the draft Bite Street is open in the editor, buttons already named): the first dish goes on offer, the offer card, entrance animations, Play and a tap on each dish
    "s_menu_b": [
        "rec:s_menu_b:175", "wait:0.5", "tap:Canvas layer play", "wait:1.2", "tapx:Text", "wait:1.2", "set:Label=Chotpoti 50", "enter",
        "tapx:Done", "wait:1.2", "tapx:Tap", "wait:1.2", "set:Message=Hot chotpoti coming up", "enter", "tapx:Done", "wait:1.2",
        "xy:30,1500", "wait:1", "tap:Canvas layer fact_text", "wait:1.2", "tapx:Text", "wait:1.2", "set:Text=Any two for 90. Till nine.", "enter",
        "tapx:Done", "wait:1.2", "xy:30,1500", "wait:1", "tap:Canvas layer title", "wait:1.2", "tapx:Effects", "wait:1.2",
        "tapx:Entrance animation: Slide bottom", "wait:1.2", "tapx:Done", "wait:1", "xy:30,1500", "wait:0.8", "tap:Canvas layer play", "wait:1.2",
        "tapx:Effects", "wait:1.2", "tapx:Entrance animation: Slide bottom", "wait:1.2", "tapx:Done", "wait:1", "xy:30,1500", "wait:0.8",
        "tap:Canvas layer hello", "wait:1.2", "tapx:Effects", "wait:1.2", "tapx:Entrance animation: Slide bottom", "wait:1.2", "tapx:Done", "wait:1",
        "xy:30,1500", "wait:0.8", "tap:Canvas layer learn", "wait:1.2", "tapx:Effects", "wait:1.2", "tapx:Entrance animation: Slide bottom", "wait:1.2",
        "tapx:Done", "wait:1", "xy:30,1500", "wait:0.8", "tapx:Play", "wait:3", "tap:Chotpoti", "wait:2.2",
        "tap:Fuchka", "wait:2.2", "tap:Jhalmuri", "wait:2.5", "endrec",
    ],
    # Tutorial C, family trivia night: from the hub a Quiz with the maker's own three questions (delete any draft Family Trivia first)
    "s_trivia_a": [
        "tohub", "rec:s_trivia_a:178", "wait:0.5", "tapx:New app", "wait:1", "tapx:Quiz", "tapx:Next", "wait:1",
        "xy:540,660", "type:Family Trivia", "xy:540,835", "type:Sam Rahman", "tapx:Next", "wait:1.2", "swipe:540,1900,540,1300,300", "wait:0.6",
        "tapx:Vivid dark", "tapx:Customize first", "wait:3", "tapx:Quiz", "wait:1.5", "tapr:Question 1: Capital of France?", "wait:1.2", "set:Question=Who burns the rice every Friday?",
        "enter", "set:Answer 1=Dad", "enter", "set:Answer 2=Mum", "enter", "set:Answer 3=Me", "enter", "wait:0.8",
        "tapr:Question 2: What is 2 + 2?", "wait:1.2", "set:Question=Whose phone is always at 1 percent?", "enter", "set:Answer 1=Me", "enter", "set:Answer 2=Sis", "enter",
        "set:Answer 3=Dad", "enter", "tapr:Answer 2 is the right answer", "wait:0.8", "tapr:Question 3: Largest planet?", "wait:1.2", "set:Question=Best biryani in the family?", "enter",
        "set:Answer 1=Grandma", "enter", "set:Answer 2=Uncle Raj", "enter", "set:Answer 3=Mum", "enter", "wait:0.8", "tapx:Done",
        "wait:1", "endrec",
    ],
    # Tutorial C part two (the quiz is open in the editor): three rounds in Play, then the past scores
    "s_trivia_b": [
        "rec:s_trivia_b:170", "wait:0.5", "tapx:Play", "wait:2.5", "tapx:Start", "wait:2", "tapx:Dad", "tapx:Sis",
        "tapx:Grandma", "wait:0.8", "tapx:Finish", "wait:2.5", "tapx:Try again", "wait:2", "tapx:Dad", "tapx:Me#1",
        "tapx:Grandma", "wait:0.8", "tapx:Finish", "wait:2.5", "tapx:Try again", "wait:2", "tapx:Mum", "tapx:Dad#1",
        "tapx:Grandma", "wait:0.8", "tapx:Finish", "wait:2.5", "tapx:Home", "wait:2", "tapx:Past scores", "wait:3.5",
        "endrec",
    ],
    # Tutorial C part one b (the Quiz panel is open with the first question closed): the three questions are written
    "s_trivia_a2": [
        "rec:s_trivia_a2:178", "wait:0.5", "tapr:Question 1: Capital of France?", "wait:1.2", "set:Question=Who burns the rice every Friday?", "enter", "set:Answer 1=Dad", "enter",
        "set:Answer 2=Mum", "enter", "set:Answer 3=Me", "enter", "wait:0.8", "tapr:Question 2: What is 2 + 2?", "wait:1.2", "set:Question=Whose phone is always at 1 percent?",
        "enter", "set:Answer 1=Me", "enter", "set:Answer 2=Sis", "enter", "set:Answer 3=Dad", "enter", "tapr:Answer 2 is the right answer",
        "wait:0.8", "tapr:Question 3: Largest planet?", "wait:1.2", "set:Question=Best biryani in the family?", "enter", "set:Answer 1=Grandma", "enter", "set:Answer 2=Uncle Raj",
        "enter", "set:Answer 3=Mum", "enter", "wait:0.8", "tapx:Done", "wait:1", "endrec",
    ],
    # Tutorial A, study streak, part one: from the hub a blank page (Workshop in its Light look), a soft backdrop colour and the title
    "s_streak_a": [
        "tohub", "rec:s_streak_a:178", "wait:0.5", "tapx:New app", "wait:1", "tapx:Blank app", "tapx:Next", "wait:1.2",
        "tapx:Design: Empty", "wait:0.6", "tapx:Next", "wait:1", "xy:540,660", "type:Study Streak", "xy:540,835", "type:Sam Rahman",
        "tapx:Next", "wait:1.2", "tapx:Light", "wait:0.6", "tapx:Start designing", "wait:3", "tapx:Backdrop", "wait:1.2",
        "swipe:540,2150,540,1850,300", "wait:0.6", "tapx:Custom", "wait:1.2", "set:Hex colour=#F3E8FF", "enter", "wait:1", "tapx:Done",
        "wait:1", "tapx:Add", "wait:1.2", "tapx:Add Text", "wait:1.5", "tapx:Text", "wait:1.2", "set:Text=Study streak",
        "enter", "tapx:Done", "wait:1.2", "xy:30,1500", "wait:1", "endrec",
    ],
    # part two (the draft Study Streak is open): a Drawing is added and its code is typed
    "s_streak_b": [
        "rec:s_streak_b:178", "wait:0.5", "tapx:Add", "wait:1.2", "tapr:Add Drawing", "wait:2", "tapx:Code", "wait:1.2",
        "tapx:Edit code", "wait:3", "xy:540,1000", "wait:1", "clearcode", "code:tools/video_code_streak.txt", "wait:2.5", "tapx:Done",
        "wait:1.2", "tapx:Done", "wait:1.5", "endrec",
    ],
    # part three: a button that runs the code, then Play and five taps
    "s_streak_c": [
        "rec:s_streak_c:178", "wait:0.5", "xy:30,1500", "tapx:Add", "wait:1.2", "tapx:Add Button", "wait:1.5", "tapx:Text",
        "wait:1.2", "set:Label=I studied today", "enter", "tapx:Done", "wait:1.2", "tapx:Tap", "wait:1.2", "tapx:Run code",
        "wait:1.5", "tapx:studied", "wait:1.2", "tapx:Done", "wait:1", "xy:30,1500", "wait:1", "tapx:Play",
        "wait:3", "tapx:I studied today", "wait:1.8", "tapx:I studied today", "wait:1.8", "tapx:I studied today", "wait:1.8", "tapx:I studied today",
        "wait:1.8", "tapx:I studied today", "wait:1.8", "wait:2.5", "endrec",
    ],
    # part two b (the draft Study Streak has its Drawing): the Code panel, the code is cleared and typed
    "s_streak_b2": ["rec:s_streak_b2:170", "wait:0.5", "tap:Canvas layer canvas_2", "wait:1", "tapx:Code", "wait:1.2", "tapx:Edit code", "wait:3", "xy:540,1000", "wait:1", "clearcode", "wait:1", "code:tools/video_code_streak.txt", "wait:3", "tapx:Done", "wait:1.2", "tapx:Done", "wait:1.5", "endrec"],
    # part four (Study Streak in design mode, button on top of the dots): the button is dragged below the drawing, then Play and seven taps fill the week
    "s_streak_d": ["rec:s_streak_d:120", "wait:0.5", "tap:Canvas layer button", "wait:1.2", "swipe:625,1341,540,2060,1100", "wait:1.5", "xy:30,1500", "wait:1", "tapx:Play", "wait:3", "tapx:I studied today", "wait:1.6", "tapx:I studied today", "wait:1.6", "tapx:I studied today", "wait:1.6", "tapx:I studied today", "wait:1.6", "tapx:I studied today", "wait:1.6", "tapx:I studied today", "wait:1.6", "tapx:I studied today", "wait:1.6", "wait:3", "endrec"],
    # Tutorial B part three: Play on the finished menu board, a tap on each dish (the messages come up)
    "s_menu_c": ["rec:s_menu_c:50", "wait:0.5", "tapx:Design", "wait:1.5", "xy:30,1500", "wait:1", "tapx:Play", "wait:4", "tap:Chotpoti", "wait:2.8", "tap:Fuchka", "wait:2.8", "tap:Jhalmuri", "wait:3", "endrec"],
    # the design picker: scroll through the starting designs without making anything
    "s_picker": [
        "tohub", "rec:s_picker:60", "wait:0.5", "tapx:New app", "wait:1.5", "tapx:Blank app", "wait:0.8", "tapx:Next", "wait:2", "swipe:540,1900,540,1300,900", "wait:1.5", "swipe:540,1900,540,1300,900", "wait:1.5",
        "swipe:540,1300,540,1900,900", "wait:1.5", "tapx:Design: Neon", "wait:1.5", "tapx:Design: Playful", "wait:1.5", "tapx:Design: Minimal", "wait:1.5", "tohub", "endrec",
    ],
    # lessons: Settings, What's new, Open the lessons, the first lessons
    "s_lessons": [
        "tohub", "rec:s_lessons:90", "wait:0.5", "tapx:Settings", "wait:1.5", "tapx:What's new", "wait:2", "tapx:Open the lessons", "wait:2.5", "text", "wait:1", "endrec",
    ],
    # the Learn screen is open (after s_lessons): a topic, a lesson with a live picture
    "s_lessons2": ["rec:s_lessons2:60", "wait:0.5", "tapx:Make it move", "wait:2.5", "tap:Going round in circles", "wait:3.5", "swipe:540,1700,540,1000,900", "wait:3", "swipe:540,1700,540,1000,900", "wait:4", "endrec"],
    # the Waiting line app in Play on its home page (draft Line Up): two people join, then they are called
    "s_queue2": [
        "rec:s_queue2:70", "wait:0.5", "tapx:Add a person", "wait:1.2", "set:Name=Maya", "enter", "set:How many people=2", "enter", "tapx:Add to the line", "wait:1.8",
        "tapx:Add a person", "wait:1.2", "set:Name=Omar", "enter", "set:How many people=1", "enter", "tapx:Add to the line", "wait:1.8", "tapx:Call next", "wait:2.2", "tapx:Call next", "wait:2.2", "wait:2", "endrec",
    ],
    # The Atelier sign in gate of the release build (never tap Sign in with Google): hub, a tap on The Atelier card, the gate, back
    "s_atelier_gate": ["tohub", "rec:s_atelier_gate:40", "wait:1.5", "tapx:The Atelier", "wait:4", "tapx:Back", "wait:2", "endrec"],
    "s_intro": ["rec:s_intro:40", "kill", "launch", "wait:9", "swipe:540,1800,540,900,500", "wait:1", "swipe:540,900,540,1800,500", "wait:1", "tap:New app", "wait:2", "endrec"],
}


def main():
    names = sys.argv[1:]
    if not names or names == ["list"]:
        print("\n".join(TAKES))
        return
    for name in names:
        print("== take", name)
        for step in TAKES[name]:
            drive.run(step)


main()
