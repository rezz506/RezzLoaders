🚀 RezzLoaders v1.0.1 — Initial Public Release

RezzLoaders is a lightweight, survival-friendly chunk loader plugin that allows server owners to give players timed 1×1 and 5×5 chunk loaders. These loaders keep farms, redstone, and machines running while players are offline — without permanently loading chunks or unnecessarily stressing the server.

This first public release focuses on stability, persistence, and redstone-safe behavior.

✨ Features

⏱ Timed chunk loaders (auto-expire & safely unload)

📦 Two variants: 1×1 and 5×5

💾 Loaders persist through server restarts

🔁 Loaders restore exactly as they were before shutdown

🏷 Hologram display 

🧱 Redstone & farm safe (does not break machines or comparators)

🔒 Permission-controlled placement

🛠 Admin tools to give, list, and remove loaders

♻ Returns the correct item if broken early

⚙️ Commands

/loader help – Show help
/loader give <player> <time> <1x1|5x5> [amount] – Give loaders
/loader list [player] – List active loaders
/loader remove <id> – Remove a loader
/loader reload – Reload config

Aliases: /chunkloader, /rloader

🔐 Permissions

rezzloaders.use – Place and use loaders

rezzloaders.give – Give loaders

rezzloaders.list – View loaders

rezzloaders.remove – Remove loaders

rezzloaders.admin – Full access

📦 Installation

Download the JAR from this release

Place it in your server’s /plugins folder

Restart the server

Use /loader give to distribute loaders

📝 Notes

Designed for Paper / modern Spigot forks

Loaders automatically unload when time expires

All data is saved and restored on restart

Includes protections to prevent chunk and redstone issues
