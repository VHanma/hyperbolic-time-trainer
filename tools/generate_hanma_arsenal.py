from pathlib import Path
from itertools import product

ROOT=Path(__file__).resolve().parents[1]
APP=ROOT/'combo-caller/app/src/main'
A=APP/'assets'
J=APP/'java/com/vhanma/purecombocaller/MainActivity.java'

D={
'Baki Hanma Inspired':(
['Predator stillness, shoulder twitch feint','Cockroach dash level change','Loose whip-hand probe, sudden stance drop','Open guard bait, pull the head off line','Compact coil, heel pulse, false retreat'],
['burst jab, rear cross, lead body hook','leaping jab, overhand, shovel hook','slip outside, cross, hook, rear knee','double jab, rear uppercut, lead hook','lead hand trap, cross, elbow, knee'],
['pivot outside and fire a rear low kick','frame the collarbone and switch stance','roll under and spring back with a jab','body lock bump, short hook, angle off','drop step overhand, lead uppercut'],
['recoil into a compact guard','circle with eyes locked on center','reset loose and ready to burst']),
'Benny The Jet Urquidez':(
['Bounce outside, jab feint, draw the check','Lead-leg chamber fake, hands high','Cross feint, step outside the lead foot','Parry the jab and hop to the blind side','Pressure step, shoulder fake, rear-hand guard'],
['jab, cross, lead hook, rear round kick','lead side kick, cross, hook, low kick','jab, rear kick, lead hook, cross','slip, cross, lead hook, spinning back kick','double jab, cross, switch kick'],
['finish with rear body kick','finish with lead hook to liver','finish with spinning backfist','finish with rear knee from collar tie','finish with axe kick feint to cross'],
['bounce out on an angle','check the return kick and counter jab','high guard, pivot, reset']),
'Bas Rutten':(
['Step forward with almost no lateral tell','Palm-jab feint, heavy lead-foot plant','High guard pressure, level feint','Catch the jab and crowd the pocket','Outside step, shoulder bump, liver-line read'],
['left straight, right palm, left hook','jab, cross, rear liver kick','lead palm, rear palm, left body hook','cross, lead hook, rear knee','rear straight, left hook, right low kick'],
['finish with the delayed liver kick','finish with double collar-tie knee','finish with palm strike to body kick','finish with left hook, right high kick','finish with rear kick as they shell'],
['lean back from the return and reset','frame, pivot, return to stance','cover, angle left, re-enter']),
'Jon Jones':(
['Long guard hand-fight, shoulder feint','Oblique-kick feint, post on the lead hand','Rear-hand frame, step to open stance','Level-change fake, collar-tie threat','Outside hand trap, stance-switch pulse'],
['oblique kick, jab, rear elbow','side kick to knee line, cross, lead hook','jab, spinning back elbow, knee','front kick, long cross, clinch elbow','lead low kick, rear straight, spinning body kick'],
['finish with collar tie and slicing elbow','finish with trip threat into knee','finish with spinning back kick','finish with short uppercut and elbow','finish with body lock turn and knee'],
['long frame and circle away','post, pivot, restore kicking range','switch stance behind a jab']),
'Nick Diaz':(
['Hands-wide bait, invite the jab','Southpaw pressure step, long pawing lead','Mugging rhythm, half-power touch jab','Draw the lead, parry over the shoulder','Walk them to the fence with shoulder feints'],
['jab, rear hook, lead body hook, lead head hook','double jab, cross, lead hook, cross','lead hook, lead hook, rear straight','body jab, rear straight, lead hook, rear hook','parry, rear straight, lead body hook, lead hook'],
['pour on four alternating punches','double the lead hand and finish cross','finish body-head-body without pausing','finish with clinch knee and break','finish with long rear straight'],
['pull back and counter their chase','triceps post, angle off, resume pressure','high forearm shell, step back in']),
'Anderson Silva':(
['Hands low, hip feint, draw the lead','Southpaw angle, cover their vision','Rear-kick feint disguised as round kick','Invite the low kick with lead leg presented','Retreat on line, sudden stance shift'],
['pull counter cross, lead hook','front snap kick up the middle','lead-hand pin, blind-angle back elbow','outside low-kick catch, rear straight','inside low-kick trap, lead elbow'],
['finish with right hook while cutting escape','finish with rear knee from plum','finish with spinning elbow','finish with body kick as they retreat','finish with cross, lead high kick'],
['lean away and reset hands low','pivot outside their power hand','frame and glide back to range']),
'Justin Gaethje':(
['High guard march, jab feint','Outside calf-kick probe, level fake','Catch-and-pitch shell, pressure step','Lead hook feint, rear-hand tight','Fence cut with small lateral step'],
['jab, overhand right, lead hook','calf kick, cross, lead uppercut','block, rear uppercut, lead hook','double jab, right low kick, left hook','lead hook, rear cross, rear low kick'],
['finish with right uppercut through the guard','finish with left hook, right calf kick','finish with collar-tie uppercut','finish with overhand as they circle','finish with body hook, head hook'],
['tight shell and angle out','check the return and fire cross','reset behind a hard jab']),
'Ilia Topuria':(
['Tiny head feint, take center','Head feint, step jab to body','Sit on the mark, read the jab','Shoulder feint, compact double step','Slip outside while loading the rear hip'],
['jab, offset right cross, lead hook','body jab, overhand right, lead hook','slip-cross, lead hook to body','double jab, rear cross, lead hook','rear cross, rear cross, lead hook'],
['finish with compact left hook to head','finish with body-head hook pair','finish with rear uppercut and left hook','finish with calf kick after the hands','finish with cross as they open guard'],
['push off the lead foot and reset','roll under the return hook','step back just outside range']),
'Sean OMalley':(
['Long stance, hand feint, hip twitch','Stance-switch pulse, false entry','Draw the jab with a lean-back target','Lead-hand post, rear-kick chamber fake','Side-step outside, eyes on the counter line'],
['jab, cross, pull, cross','feint cross, lead hook, rear straight','switch jab, rear body kick, lead hook','pull counter cross, lead uppercut','jab, spinning back kick, cross'],
['finish with check hook while angling','finish with step-up knee','finish with high kick behind the punch','finish with spinning backfist','finish with long cross as they chase'],
['slide out and switch stance','long guard, pivot, reset','bounce back to sniper range']),
'Georges St-Pierre':(
['Level-change feint beneath the jab','Double-jab entry, rear hip loaded','Superman-punch chamber, low-kick threat','Outside step, pawing jab, wrestling stance','Jab feint, penetration-step rhythm'],
['jab, cross, double leg','superman punch, rear low kick','double jab, body kick, level change','jab, lead hook, blast double','inside low kick, jab, rear straight'],
['finish with fence turn and knee','finish with high-crotch lift threat','finish with ground-position call','finish with rear kick after the reset','finish with jab as they defend takedown'],
['circle off behind the jab','frame and return to wrestling stance','sprawl-ready stance, hands high']),
'Lyoto Machida':(
['Wide karate stance, half-step draw','Retreating bait, sudden hip burst','Lead-hand range finder, outside foot step','Rear-round-kick feint, stance frozen','In-and-out bounce, intercept the entry'],
['blitz cross, lead hook, rear straight','rear round kick, rear straight before landing','front snap kick, reverse punch','step-back cross, lead knee','lead side kick, cross, rear round kick'],
['finish with outside trip threat','finish with lead hook as they turn','finish with body kick and slide away','finish with straight knee on entry','finish with rear hand down the center'],
['spring straight back to long range','angle to the open side','reset in wide stance']),
'Miyamoto Musashi Inspired':(
['Position without position, show middle guard','Holding down the pillow, intercept the first twitch','Short-armed monkey, move the whole body inside','Crossing the ford, wait at the difficult transition','Crimson foliage, strike the attacking limb first'],
['single-beat cross, lead hook','second-spring jab, delayed rear straight','flowing-water parry, body hook, head hook','sticky-body frame, shoulder bump, uppercut','three-parry hand fight, straight down center'],
['change mountain to sea: pressure then angle','body instead of sword: chest bump to short hook','stomp the attack rhythm with a jab','glue the guard, strike around it','compare height, close tall with knee'],
['complementary steps, never freeze','clear the line and regain middle position','observe broadly and reset without fixation']),
'Sun Tzu Inspired':(
['Appear weak, invite the advance','Show high attack, prepare the low line','False retreat, leave an apparent opening','Attack the empty side after drawing the guard','Use terrain: cut the escape lane'],
['retreat half-step, cross, lead hook','high jab feint, body cross, low kick','slow jab rhythm, sudden double-time cross','touch lead hand, angle, rear straight','level feint, overhand, body hook'],
['turn their momentum with a check hook','strike where the guard is absent','divide attention with body-head-body','press advantage before they reorganize','change the pattern before repetition'],
['leave by the route they abandoned','restore distance and conceal the next intent','circle to favorable terrain']),
}

A.mkdir(parents=True,exist_ok=True)
rows=['# source\tbasis\tcall']
for src,(s,a,f,r) in D.items():
    basis=('Inspired tactical conversion' if 'Inspired' in src else 'Researched signature and style-faithful expansion')
    combos=[]
    for x,y,z,w in product(s,a,f,r):
        c=f'{x}; {y}; {z}; {w}'
        if c not in combos: combos.append(c)
        if len(combos)==100: break
    assert len(combos)==100,src
    rows += [f'{src}\t{basis}\t{c}' for c in combos]
(A/'fighter_combos.tsv').write_text('\n'.join(rows)+'\n')

p=J.read_text()
p=p.replace('package com.vhanma.purecombocaller;','package com.vhanma.hanmaarsenalcaller;')
p=p.replace('readAsset("combos_named.txt", "Named combinations");','readAsset("combos_named.txt", "Named combinations");\n        readTsvAsset("fighter_combos.tsv");')
needle='''    private String classify(String raw, String source) {'''
method='''    private void readTsvAsset(String filename) {\n        try (BufferedReader reader = new BufferedReader(new InputStreamReader(getAssets().open(filename)))) {\n            String line;\n            while ((line = reader.readLine()) != null) {\n                if (line.startsWith("#") || line.trim().isEmpty()) continue;\n                String[] parts = line.split("\\\\t", 3);\n                if (parts.length == 3) allCombos.add(new Combo(parts[2].trim(), parts[0].trim()));\n            }\n        } catch (Exception e) { Toast.makeText(this, "Missing " + filename, Toast.LENGTH_LONG).show(); }\n    }\n\n'''
p=p.replace(needle,method+needle)
old='''        Spinner category = spinner(new String[]{\n                "All combinations",\n                "Coded library",\n                "Named boxing",\n                "Muay Thai and kickboxing",\n                "Defense and footwork"\n        });\n        Spinner order = spinner(new String[]{"Random", "Shuffle deck", "Sequential"});'''
new='''        List<String> libraryValues = new ArrayList<>();\n        libraryValues.add("All combinations");\n        libraryValues.add("Coded library");\n        libraryValues.add("Named boxing");\n        libraryValues.add("Muay Thai and kickboxing");\n        libraryValues.add("Defense and footwork");\n        List<String> fighterSources = new ArrayList<>(categoryCounts.keySet());\n        Collections.sort(fighterSources);\n        for (String sourceName : fighterSources) if (!libraryValues.contains(sourceName)) libraryValues.add(sourceName);\n        Spinner category = spinner(libraryValues.toArray(new String[0]));\n        Spinner order = spinner(new String[]{"FULL RANDOM", "SHUFFLE ALL — NO REPEAT", "Sequential"});'''
if old not in p: raise SystemExit('spinner block missing')
p=p.replace(old,new)
p=p.replace('orderMode = "Random";','orderMode = "FULL RANDOM";')
p=p.replace('if (orderMode.equals("Shuffle deck")) Collections.shuffle(activeCombos, random);','if (orderMode.startsWith("SHUFFLE")) Collections.shuffle(activeCombos, random);')
p=p.replace('if (orderMode.equals("Sequential") || orderMode.equals("Shuffle deck")) {','if (orderMode.equals("Sequential") || orderMode.startsWith("SHUFFLE")) {')
p=p.replace('if (orderMode.equals("Shuffle deck")) Collections.shuffle(activeCombos, random);','if (orderMode.startsWith("SHUFFLE")) Collections.shuffle(activeCombos, random);')
p=p.replace('PURE COMBO CALLER','HANMA ARSENAL CALLER')
p=p.replace('NO CAMERA  •  NO VIDEO  •  JUST COMBINATIONS','2,198 COMBOS  •  FIGHTERS  •  BAKI  •  MUSASHI  •  SUN TZU')
p=p.replace('private static final int CYAN = 0xFF62E7FF;','private static final int CYAN = 0xFFFF3B30;')
J.parent.mkdir(parents=True,exist_ok=True)
newJ=ROOT/'combo-caller/app/src/main/java/com/vhanma/hanmaarsenalcaller/MainActivity.java'
newJ.parent.mkdir(parents=True,exist_ok=True)
newJ.write_text(p)
J.unlink()

manifest=APP/'AndroidManifest.xml'
m=manifest.read_text().replace('com.vhanma.purecombocaller','com.vhanma.hanmaarsenalcaller').replace('Pure Combo Caller','Hanma Arsenal Caller')
manifest.write_text(m)
g=ROOT/'combo-caller/app/build.gradle'
t=g.read_text().replace("namespace 'com.vhanma.purecombocaller'","namespace 'com.vhanma.hanmaarsenalcaller'").replace("applicationId 'com.vhanma.purecombocaller'","applicationId 'com.vhanma.hanmaarsenalcaller'").replace("versionCode 1","versionCode 2").replace("versionName '1.0-PureCaller'","versionName '2.0-HanmaArsenal'")
g.write_text(t)
print('generated',len(rows)-1,'fighter/strategy chains; total intended',len(rows)-1+898)
