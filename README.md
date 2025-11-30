MinecraftEconomyPlugin

Status

License





Project Overview

The MinecraftEconomyPlugin is a comprehensive core plugin designed to enhance the Minecraft server experience by integrating economic management, a graphical user interface (GUI) shop system, essential travel utilities, and various quality-of-life features necessary for modern server operation.

A recent addition includes the Single Player Sleep functionality, ensuring small servers can transition to daytime without requiring all players to be in bed.

Core Features

1. Unified Economy System

Manages server currency and facilitates secure player-to-player money transfers (/pay).

Administrative commands for setting, adding, and removing player balances (/setmoney, /addmoney, /removemoney).

2. GUI Shop Interface

Provides an intuitive, inventory-based menu (/menu) for easy buying and selling of items.

3. Teleportation and Travel Utilities

Allows players to save and teleport to personal home locations (/sethome, /home).

Enables teleportation to the server spawn point (/spawn).

Implements a reliable Player-to-Player Teleport (TPA) request system (/tpa, /tpaccept, /tpdeny).

4. Quality of Life (QoL) and Administration Enhancements

Single Player Sleep: Automatically sets the time to day when a single, eligible player enters a bed during the night.

Gambling System: Allows administrators to set up a designated gambling block (/setgamble) for player interaction and automated betting.

Player nickname management.

Administrative Jail/Unjail system with timed release functionality.

Installation

Download Plugin:

Obtain the latest MinecraftEconomyPlugin.jar file from the Releases Page.

Deployment:

Upload the downloaded .jar file to your Minecraft server's plugins directory.

Activation:

Restart the server or use the /reload command to activate the plugin.

Command Reference

Command

Permission Node

Description

/menu

None

Opens the main economy menu interface.

/money

None

Displays the player's current balance.

/pay <player> <amount>

None

Transfers money to another player.

/sethome <name>

None

Saves the current location as a named home.

/home <name>

None

Teleports the player to a saved home location.

/tpa <player>

None

Requests teleportation to a target player.

/tpaccept

None

Accepts an incoming teleport request.

/setmoney <player> <amount>

economy.admin

Sets the specified player's money balance.

/jail <player> <ticks>

economy.admin

Sends a player to the designated jail location for a specific duration (in ticks).

/unjail <player>

economy.admin

Immediately releases a player from jail.

/setgamble

economy.admin

Sets the location of the main gambling block/area for the server.

/name <nickname>

economy.name

Sets or changes the player's displayed nickname.

Contributing

We welcome contributions! If you encounter any bugs, have feature requests, or wish to submit code changes, please follow these steps:

Fork the repository.

Create your feature branch (git checkout -b feature/new-feature).

Commit your changes (git commit -m 'Feat: Implement new feature').

Push to the branch (git push origin feature/new-feature).

Open a Pull Request.

License

This project is licensed under the MIT License. See the LICENSE file for full details.

Developed by: [chanwookim-cloud]
