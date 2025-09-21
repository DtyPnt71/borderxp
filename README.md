**Beta**
|[GitHub](https://github.com/DtyPnt71/borderxp/tree/main)|
**FILES FOR PAPER WILL BE UPLOADED SOON**
**BorderXP by BrnSvr**

**Looking for someone, to make this project compatible with**
**Fabric, [X]Bukkit, Forge, NeoForge compatible**

## 🌟 Features
- 🌍 **Dynamic world border** – grows automatically with the global level*  
- ⚡ **Configurable multiplier** – control how much each level expands the border  
- 📏 **Configurable maximum size** – never too large  
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
## **🧪 How Global XP Works**
BorderXP introduces a **global XP system** that synchronizes experience levels across all players on the server.  
Instead of each player keeping their own XP, the plugin maintains a **shared global level**:

- When a player gains or loses XP, the plugin updates the **global XP level**  
- This global value is synced back to all players  
- The **world border size** is calculated from this global level (using your configured multiplier)  
- Progress becomes cooperative – the more XP your team collects, the bigger the world becomes  

---

## 🎮 Player Tracker in the XP Bar
To make this feel natural, BorderXP uses the **Minecraft XP bar** as a **progress tracker**:

- The **green XP bar** under the hotbar shows how close the server is to the **next global level**  
- Once enough XP is collected, the **global level increases** for everyone  
- Because the XP bar is shared, all players can immediately see the team’s progress  
- The XP bar becomes a **community progress tracker**, not just a personal stat  

---

👉 Instead of XP being a private resource, BorderXP turns it into a **shared team goal**:  
work together to **level up the world** and unlock more territory by expanding the border.

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


## ✅ Compatibility

- Works with Sponge 1.21.8 (API 8/9)
  ```text
  Tested with SpongeVanilla server

## 🤝 Contributing

Contributions are very welcome! 🎉
Feel free to fix issues, add new features, or submit forks.
This project is meant to grow together with the community — just like the border expands with your XP. 😉

---

```
GNU GENERAL PUBLIC LICENSE
Version 3, 29 June 2007

Copyright (C) 2025  BrnSvr

```
