BorderXP
✨ Overview

BorderXP is a Sponge plugin (Minecraft 1.21.x) that ties the world border size to the global experience level of your players.
As players gain XP, the border expands — making the world grow together with your progress.

🌟 Features

🌍 Dynamic world border – grows automatically with the global level.

⚡ Configurable multiplier – control how much each level expands the border.

📏 Minimum and maximum size – never too small or too large.

⏱ Global playtime timer – shows up in the action bar.

🧾 Customizable messages – with color codes and placeholders:
{time}, {max}, {target}, {version}

💬 Simple commands:

/borderxp info → show plugin info

/borderxp set size <value> → set border size

/borderxp set multiplier <value> → change the multiplier

/borderxp timer → toggle timer display

/borderxp reload → reload configuration

📦 Installation

Download the latest release from GitHub
 or Modrinth
.

Place the .jar file into your Sponge server’s mods/ folder.

Start the server once to generate the config file:

config/borderxp.properties


Adjust the settings to your liking.

⚙️ Configuration

Default config (borderxp.properties):

multiplier=2.0
minDiameter=2.0
maxDiameter=1000000.0
tickInterval=20
center=spawn
showTimerDefault=true


You can also customize all messages with color codes (&a, &b, &#RRGGBB) and placeholders.

✅ Compatibility

Works with Sponge 1.21.x (API 8/9).

Tested with SpongeVanilla servers.
