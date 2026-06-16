import json
import random

# We generate a structured fine-tuning dataset for MiniCPM-1B.
# To prevent model confusion, we balance the training examples between:
# 1. Routing Intent: User Query -> Route Target Name (e.g. [route: system])
# 2. Tool Executions: Agent Context & Prompt -> Structured Tool JSON Code (e.g. <start_function_call>...)

dataset = []

# --- ROUTER DIALOGUES ---
router_intents = [
    # System Control queries
    ("turn on the flashlight", "[route: system]"),
    ("disable wi-fi and turn on bluetooth", "[route: system]"),
    ("set the ringer volume to 50 percent", "[route: system]"),
    ("mute my phone and turn off cellular", "[route: system]"),
    ("can you dim the screen to 30%", "[route: system]"),
    ("please enable flashlight", "[route: system]"),
    ("adjust screen brightness to 100", "[route: system]"),
    ("disconnect wifi", "[route: system]"),
    ("enable auto-rotation on my screen", "[route: system]"),
    ("disable screen rotation", "[route: system]"),
    # Media Player queries
    ("play some acoustic rock music", "[route: media]"),
    ("pause the music playback", "[route: media]"),
    ("resume the song please", "[route: media]"),
    ("skip to the next track", "[route: media]"),
    ("play my chill beats playlist", "[route: media]"),
    ("stop the video audio", "[route: media]"),
    ("can you play some lo-fi jazz", "[route: media]"),
    # Camera queries
    ("open the camera and take a vintage picture", "[route: camera]"),
    ("take a photo using the black and white noir filter", "[route: camera]"),
    ("capture a portrait shot now", "[route: camera]"),
    ("snap a photo with neon glow visualizer", "[route: camera]"),
    ("open the viewfinder", "[route: camera]"),
    ("take a retro style snapshot", "[route: camera]"),
    # Productivity/Calendar queries
    ("schedule a project sync for tomorrow at 2 PM", "[route: productivity]"),
    ("remind me to call mom in 30 minutes", "[route: productivity]"),
    ("add a meeting called design review on friday at 11 AM", "[route: productivity]"),
    ("set an alarm for 6:30 tomorrow morning", "[route: productivity]"),
    ("plan a lunch meeting with alex today at 1 o'clock", "[route: productivity]"),
    ("create a task to buy milk", "[route: productivity]"),
    ("show me my upcoming calendar appointments", "[route: productivity]")
]

# Generate multiple phrasing structures for the Router Agent SFT targets
router_system_prompt = "You are the central intent router for AegisAgent. Classify the user instruction to one of: [route: system], [route: media], [route: camera], or [route: productivity]."

for query, target in router_intents:
    # Standard format
    dataset.append({
        "messages": [
            {"role": "system", "content": router_system_prompt},
            {"role": "user", "content": query},
            {"role": "assistant", "content": target}
        ]
    })
    # Variation with politeness/prefixes
    dataset.append({
        "messages": [
            {"role": "system", "content": router_system_prompt},
            {"role": "user", "content": f"Hey assistant, can you please {query.lower()}?"},
            {"role": "assistant", "content": target}
        ]
    })
    # Variation with quick/command syntax
    dataset.append({
        "messages": [
            {"role": "system", "content": router_system_prompt},
            {"role": "user", "content": query.upper()},
            {"role": "assistant", "content": target}
        ]
    })


# --- SYSTEM TOOL EXECUTION DIALOGUES ---
system_system_prompt = "You are the System Agent. Execute system hardware commands using set_flashlight, set_volume, set_brightness, configure_networks, or set_rotation."

system_tool_calls = [
    ("turn on the flashlight", '<start_function_call>device.set_flashlight{state: true}<end_function_call>'),
    ("turn off flashlight", '<start_function_call>device.set_flashlight{state: false}<end_function_call>'),
    ("set volume to 80%", '<start_function_call>device.set_volume{level: 80}<end_function_call>'),
    ("mute system audio", '<start_function_call>device.set_volume{level: 0}<end_function_call>'),
    ("make brightness 50", '<start_function_call>device.set_brightness{level: 50}<end_function_call>'),
    ("maximize screen brightness", '<start_function_call>device.set_brightness{level: 100}<end_function_call>'),
    ("turn off wi-fi and enable bluetooth", '<start_function_call>device.configure_networks{wifi: false, bluetooth: true, cellular: true}<end_function_call>'),
    ("disable all wireless networks", '<start_function_call>device.configure_networks{wifi: false, bluetooth: false, cellular: false}<end_function_call>'),
    ("enable auto-rotation", '<start_function_call>device.set_rotation{state: true}<end_function_call>'),
    ("lock screen rotation", '<start_function_call>device.set_rotation{state: false}<end_function_call>')
]

for query, tool_call in system_tool_calls:
    dataset.append({
        "messages": [
            {"role": "system", "content": system_system_prompt},
            {"role": "user", "content": query},
            {"role": "assistant", "content": tool_call}
        ]
    })


# --- MEDIA TOOL EXECUTION DIALOGUES ---
media_system_prompt = "You are the Media Agent. Control audio and playback using play_music or control_playback."

media_tool_calls = [
    ("play some chill synthwave", '<start_function_call>media.play_music{track: "synthwave", playing: true}<end_function_call>'),
    ("play lo-fi hip hop beats", '<start_function_call>media.play_music{track: "lo-fi hip hop", playing: true}<end_function_call>'),
    ("pause the track", '<start_function_call>media.control_playback{playing: false}<end_function_call>'),
    ("resume playing", '<start_function_call>media.control_playback{playing: true}<end_function_call>'),
    ("stop the music player", '<start_function_call>media.control_playback{playing: false}<end_function_call>')
]

for query, tool_call in media_tool_calls:
    dataset.append({
        "messages": [
            {"role": "system", "content": media_system_prompt},
            {"role": "user", "content": query},
            {"role": "assistant", "content": tool_call}
        ]
    })


# --- CAMERA TOOL EXECUTION DIALOGUES ---
camera_system_prompt = "You are the Camera Agent. Open camera viewfinder and snap pictures using take_photo."

camera_tool_calls = [
    ("take a photo", '<start_function_call>camera.take_photo{filter: "default"}<end_function_call>'),
    ("snap a photo with vintage lens", '<start_function_call>camera.take_photo{filter: "vintage"}<end_function_call>'),
    ("capture black and white noir picture", '<start_function_call>camera.take_photo{filter: "noir"}<end_function_call>'),
    ("take neon glow photo", '<start_function_call>camera.take_photo{filter: "neon"}<end_function_call>')
]

for query, tool_call in camera_tool_calls:
    dataset.append({
        "messages": [
            {"role": "system", "content": camera_system_prompt},
            {"role": "user", "content": query},
            {"role": "assistant", "content": tool_call}
        ]
    })


# --- PRODUCTIVITY TOOL EXECUTION DIALOGUES ---
productivity_system_prompt = "You are the Productivity Agent. Manage calendar events and reminders using add_meeting or create_reminder."

productivity_tool_calls = [
    ("schedule a project sync tomorrow at 3 PM", '<start_function_call>calendar.add_meeting{title: "project sync", date: "tomorrow", time: "3 PM"}<end_function_call>'),
    ("add meeting called design review for friday at 11 AM", '<start_function_call>calendar.add_meeting{title: "design review", date: "friday", time: "11 AM"}<end_function_call>'),
    ("remind me to stand up in 20 minutes", '<start_function_call>calendar.create_reminder{title: "stand up", delay_minutes: 20}<end_function_call>'),
    ("set a focus timer for 45 minutes", '<start_function_call>calendar.create_reminder{title: "focus session", delay_minutes: 45}<end_function_call>')
]

for query, tool_call in productivity_tool_calls:
    dataset.append({
        "messages": [
            {"role": "system", "content": productivity_system_prompt},
            {"role": "user", "content": query},
            {"role": "assistant", "content": tool_call}
        ]
    })


# Shuffle and balance samples
random.shuffle(dataset)

# Save output to dataset.jsonl
with open("dataset.jsonl", "w") as f:
    for item in dataset:
        f.write(json.dumps(item) + "\n")

print(f"Dataset successfully compiled! Generated {len(dataset)} balanced training records in dataset.jsonl")
