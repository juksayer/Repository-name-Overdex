Consider replacing generic value with event-specific payloads once BattleEvent stabilizes

## Handheld vs Android Interaction

The search keyboard currently transitions the user from the Overdex handheld experience into a standard Android interaction.

This is not inherently a problem, but it creates two different interaction models:

- Overdex handheld
- Android touch input

The more of Overdex that can be navigated with the D-pad and physical controls, the longer the player remains inside the handheld metaphor.

This should be treated as a long-term design direction rather than an immediate implementation task.

Future exploration:
- Search without immediately invoking the Android keyboard.
- D-pad-first browsing.
- Device-native text entry concepts.
- Quick filters or shortcuts that reduce the need for typing.