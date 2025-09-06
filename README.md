# BorderXP

## ✨ Overview
**BorderXP** is a Sponge plugin (Minecraft **1.21.x**) that ties the **world border size** to the **global experience level** of your players.  
As players gain XP, the border expands — making the world grow together with your progress.

---

## 🌟 Features
- 🌍 **Dynamic world border** – grows automatically with the global level  
- ⚡ **Configurable multiplier** – control how much each level expands the border  
- 📏 **Minimum and maximum size** – never too small or too large  
- ⏱ **Global playtime timer** – shows up in the action bar  
- 🧾 **Customizable messages** – with color codes and placeholders  
  - Placeholders: `{time}`, `{max}`, `{target}`, `{version}`
- 💬 **Commands**:
  - `/borderxp info` → show plugin info  
  - `/borderxp set size <value>` → set border size  
  - `/borderxp set multiplier <value>` → change the multiplier  
  - `/borderxp timer` → toggle timer display  
  - `/borderxp reload` → reload configuration  

---

## 📦 Installation
1. Download the latest release from [Modrinth](https://modrinth.com/project/borderxp).  
2. Place the `.jar` file into your Sponge server’s `mods/` folder.  
3. Start the server once to generate the config file:  

   ```text
   config/borderxp.properties

## ⚙️ Configuration

- Default borderxp.properties:

   ```
   multiplier=2.0
   minDiameter=2.0
   maxDiameter=1000000.0
   tickInterval=20
   center=spawn
   showTimerDefault=true
- You can also customize all messages with color codes (&a, &b, &#RRGGBB) and placeholders.
