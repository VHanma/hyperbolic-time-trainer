# Hanma Combo Caller R3 v3.1.1

Side-by-side clone of the Pure Combo Caller, rebuilt as an offline tactical fight simulator.

## Package
`com.vhanma.hanmacombocallerr3`

## R3 systems
- Preserves all 898 supplied combo slots in Pure Library mode.
- Opponent Brain: adaptive rival, pressure fighter, counter striker, wrestler, southpaw sniper, high-guard shell, long kicker, dirty boxer, submission hunter, chaos rival.
- Tactical grammar: read -> defense/manipulation -> attack -> continuation -> maneuver/phase transition.
- Fight Simulator feedback buttons: LANDED, DEFENDED, RESET STATE. The rival adapts to the result.
- Fighter DNA Mixer: blend three profiles by percentage.
- Profiles: Hanma Adaptive, Ilia Topuria, prime Conor McGregor, Benny Urquidez, Bas Rutten, Jon Jones, Nick Diaz, Anderson Silva, Justin Gaethje, Sean O'Malley, Georges St-Pierre, Lyoto Machida, Miyamoto Musashi, Sun Tzu, and 52 Blocks Sentinel.
- Strategy layers: Hanma Synthesis, Musashi Initiative, Sun Tzu Manipulation, 52 Blocks Sentinel, Scientific MMA, Chaos Adaptation.
- Round personas: counter, pressure, body attack, southpaw, wrestling threat, cage, survival, finisher, chaos.
- Range-state engine: outside -> kicking -> boxing -> pocket -> clinch -> wrestling -> ground with legal phase bridges.
- Stance state: orthodox, southpaw, auto-switch.
- Automatic difficulty evolution based on session success/failure feedback.
- Adaptive tactical memory rotates themes, avoids stale openings, and changes patterns after failure feedback.
- Open Exchange mode gives tactical constraints instead of prescribing every technique.
- Reaction Flash mode for immediate perception-action calls.
- Skill Shards mode tracks reps to an AUTOMATIC threshold and starts combining mastered shards.
- Persistent shard progress in local app storage.
- Custom Technique Lab syntax: `Name | RANGE | ROLE | tags`.
- Coach interruptions and optional WHY explanations.
- TTS-completion-aware pacing so recovery time starts after the call finishes.
- Anti-stupid-combo filter now distinguishes punches, shots, clinch attacks, teeps, body kicks, and low kicks when choosing defenses.
- Manual feedback cancels the pending automatic cue cleanly, preventing double calls.
- No camera, microphone, account, network permission, or cloud dependency.
