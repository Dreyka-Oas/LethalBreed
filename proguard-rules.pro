# =====================================================================================
# LethalBreed — ProGuard rules (post-remap, Fabric-safe)
#
# GOAL: make the shipped jar as unreadable as possible WITHOUT breaking the mod loader.
# On the JVM full protection is impossible (a .jar is a ZIP, .class is decompilable), so
# this maximises friction: strip ALL debug info + rename every purely-internal symbol.
# Everything the loader/mixin/serialization touches by NAME is kept verbatim.
#
# Runs AFTER Loom's remapJar (classes already in intermediary + our own named classes).
# =====================================================================================

# ---- Global: keep it working, kill readability ----
-dontshrink                         # do not remove code (avoids nuking reflectively/loader-used bits)
-dontoptimize                       # optimization can reorder/inline in ways that confuse Mixin @Inject
-allowaccessmodification
-repackageclasses 'com.dreykaoas.lethalbreed.z'   # flatten renamed internals into one opaque package
-overloadaggressively
-adaptclassstrings                  # rewrite string class-names for renamed classes (keeps reflection consistent)
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,RuntimeInvisibleAnnotations,AnnotationDefault,Signature,*Annotation*,EnclosingMethod,InnerClasses

# STRIP DEBUG: no line numbers, no local-variable names, no source file, no params.
# This is the single biggest readability killer for a decompiler.
# (SourceFile / LineNumberTable / LocalVariable* are simply NOT in -keepattributes above,
#  so they are dropped. Explicitly rename source file to nothing useful.)
-renamesourcefileattribute ''

# ---- Warnings from Minecraft/Fabric/JOCL supertypes we don't feed as libraryjars perfectly ----
# The remapJar output is in INTERMEDIARY mappings (method_*/class_*/field_*), but runtimeClasspath
# supplies Minecraft in NAMED (Mojang) mappings — so ProGuard can't resolve the intermediary MC
# refs our classes call. That is fine: we never rename MC symbols (they're kept via -keep and the
# coupled layer), and with -dontshrink/-dontoptimize the only renaming is our internal List 3.
# So the unresolved-MC warnings are non-fatal noise. Ignore them.
-ignorewarnings
-dontnote
-dontwarn net.minecraft.**
-dontwarn net.fabricmc.**
-dontwarn org.jocl.**
-dontwarn com.mojang.**
-dontwarn org.spongepowered.**
-dontwarn com.google.**
-dontwarn org.slf4j.**

# =====================================================================================
# LIST 1 — Entrypoints + Mixins: referenced by name in fabric.mod.json / mixins.json.
# Rename any of these and the mod does not boot. Keep class + all members.
# =====================================================================================
-keep class com.dreykaoas.lethalbreed.LethalBreedMod { *; }
-keep class com.dreykaoas.lethalbreed.client.LethalBreedClient { *; }
-keep class com.dreykaoas.lethalbreed.mixin.** { *; }

# Interface duck-cast by a mixin (LivingEntityRenderStateMixin implements it) — keep intact.
-keep interface com.dreykaoas.lethalbreed.client.BellyChargeHolder { *; }

# =====================================================================================
# LIST 2 — MC-coupled: subclass/override MC types. Overrides carry MC-mapped names that
# MUST match at runtime. Keep the classes' members (class names themselves may stay too —
# cheapest correct rule is keep-members on the whole coupled surface).
# =====================================================================================
-keep class com.dreykaoas.lethalbreed.effect.SuperContaminationEffect { *; }
-keep class com.dreykaoas.lethalbreed.effect.LeapEffect { *; }
-keep class com.dreykaoas.lethalbreed.phase.PhaseSavedData { *; }
-keep class com.dreykaoas.lethalbreed.client.screen.CustomConfigScreen { *; }
-keep class com.dreykaoas.lethalbreed.client.screen.CategoryList { *; }
-keep class com.dreykaoas.lethalbreed.client.screen.OptionList { *; }
-keep class com.dreykaoas.lethalbreed.client.screen.OptionEntry { *; }
-keep class com.dreykaoas.lethalbreed.client.screen.OptionEntry$* { *; }
-keep class com.dreykaoas.lethalbreed.net.LethalConfigPayloads { *; }
-keep class com.dreykaoas.lethalbreed.net.LethalConfigPayloads$* { *; }

# Registration classes: register(...) wires holders by ResourceLocation. Keep members so
# static registry holders + their init order survive.
-keep class com.dreykaoas.lethalbreed.effect.LethalBreedEffects { *; }
-keep class com.dreykaoas.lethalbreed.effect.LethalBreedPotions { *; }
-keep class com.dreykaoas.lethalbreed.sound.SoundEventBus { *; }
-keep class com.dreykaoas.lethalbreed.entity.ZombieRegistry { *; }
-keep class com.dreykaoas.lethalbreed.entity.ZombieVariation { *; }

# Any class that OVERRIDES a Minecraft method must not have that override renamed. Blanket-safe:
# never rename members that override a net.minecraft.* / mixin-visible method. ProGuard keeps
# overrides of kept supertypes automatically, but MC supertypes are libraryjars (kept), so this
# is covered. Belt-and-suspenders for the wrapper layer that calls MC-mapped APIs heavily:
-keepclassmembers class com.dreykaoas.lethalbreed.entity.SmartZombie { *; }
-keepclassmembers class com.dreykaoas.lethalbreed.entity.move.** { *; }
-keepclassmembers class com.dreykaoas.lethalbreed.phase.ZombieEquipper { *; }
-keepclassmembers class com.dreykaoas.lethalbreed.phase.PhaseManager { *; }
-keepclassmembers class com.dreykaoas.lethalbreed.effect.ContaminationManager { *; }

# =====================================================================================
# REFLECTION / SERIALIZATION — name-sensitive, keep the exact names.
# =====================================================================================
# ConfigSchema enumerates holder.getDeclaredFields() and addresses config options by
# f.getName() (config file keys + network SetConfig(field,value)). Renaming these fields
# breaks config load AND existing saved config files. Keep field NAMES on every holder.
-keepclassmembernames class com.dreykaoas.lethalbreed.config.domain.** {
    public static <fields>;
}
# ConfigSchema itself does the reflection — keep its member names.
-keepclassmembernames class com.dreykaoas.lethalbreed.config.ConfigSchema { *; }

# Codec / SavedData record getters used by RecordCodecBuilder lambdas + attachment string keys.
-keepclassmembers class com.dreykaoas.lethalbreed.special.SpecialAttachment { *; }

# Enum values() / valueOf() are used across config + special-type parsing — keep for all enums.
-keepclassmembers enum com.dreykaoas.lethalbreed.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Records: keep component accessors (Codec / pattern use).
-keepclassmembers class com.dreykaoas.lethalbreed.** {
    private final <fields>;
}
-keep,allowobfuscation @interface *

# =====================================================================================
# Everything ELSE under com.dreykaoas.lethalbreed.** (List 3: flow field, GPU wrappers,
# math, tick, spatial, LOD, data holders) is renamed + repackaged + stripped of debug.
# No rule = fair game. That is the intent.
# =====================================================================================
