**Beta**
|[GitHub](https://github.com/DtyPnt71/borderxp/tree/main)|

**BorderXP by BrnSvr**
## ⚠️Coming soon⚠️
- Shrunk / Growth sound (configurable)
- Change Round to Square Border generation (_Currently you get damage in the corners because of sqrt reference_)
- Respawn protection (configurable)
- Better *.properties structure

## Features
  **[Sponge] (discontinued)**
- **EXP-based Border**: Diameter scales with the global level.
- **Configurable**: simple configuration
- **Global State**: shared level for all players, playtime timer
---
  **[Paper]**
- **EXP-based Border**: Diameter scales with the global level.
- **Configurable**: simple configuration
- **Global State**: shared level for all players, playtime timer
- **Death Handling**: configurable retain multiplier + respawn at center
- **Border Feedback**: grow/shrink announcements shows added size in chat
- **Outside Warning**: 5-second countdown with sound
- **Punishment**: damage only after finished countdown, when stay outside
- **Custom Messages**: edit chat texts with `&` color codes in `BorderXP.properties`.
- **_Java 17 compatible_**


## Commands
  - `/borderxp info` → show plugin info  
  - `/borderxp set size <value>` → set border size (buggy)
  - `/borderxp set multiplier <value>` → change the multiplier  
  - `/borderxp timer` → toggle timer display  

## Permissions
```
borderxp.admin – full access
borderxp.set
borderxp.set.size
borderxp.set.multiplier
borderxp.center – allow set center of Border
borderxp.timer – allow timer toggle ON/OFF
```

---
## **How Global XP Works**
BorderXP introduces a **global XP system** that synchronizes experience levels across all players on the server.  
Instead of each player keeping their own XP, the plugin maintains a **shared global level**:

- When a player gains or loses XP, the plugin updates the **global XP level**  
- This global value is synced back to all players  
- The **world border size** is calculated from this global level (using your configured multiplier)  
- Progress becomes cooperative – the more XP your team collects, the bigger the world becomes  

---

## Important
The Sponge edition of **BorderXP** is **more challenging**.

- Because of current SpongeAPI/Minecraft limitations, spawning hostile mobs outside the world
  border isn’t supported.

The Paper edition it is working fine!

---

Instead of XP being a private resource, BorderXP turns it into a **shared team goal**:  
work together to **level up the world** and unlock more territory by expanding the border.

## Installation
1. Download the latest release from [Modrinth](https://modrinth.com/project/borderxp).  
2. Place the `.jar` file into your `mods/` (Sponge) or `plugins/` (Paper)  folder.  
