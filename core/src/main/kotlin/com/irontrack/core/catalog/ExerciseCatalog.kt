package com.irontrack.core.catalog

import com.irontrack.core.util.TextNormalizer

/**
 * Exercice de référence de la bibliothèque IronTrack.
 *
 * [key] est un identifiant stable (jamais affiché, jamais traduit) qui permet de retrouver
 * le même exercice d'une installation à l'autre et lors des imports.
 */
data class CatalogExercise(
    val key: String,
    val nameFr: String,
    val nameEn: String,
    val muscleGroup: String,
    val equipment: String,
    val synonyms: List<String> = emptyList(),
)

object ExerciseCatalog {

    // Groupes musculaires (libellés français affichés tels quels).
    const val PECS = "Pectoraux"
    const val DOS = "Dos"
    const val EPAULES = "Épaules"
    const val BICEPS = "Biceps"
    const val TRICEPS = "Triceps"
    const val QUADRICEPS = "Quadriceps"
    const val ISCHIOS = "Ischio-jambiers"
    const val FESSIERS = "Fessiers"
    const val MOLLETS = "Mollets"
    const val ABDOS = "Abdominaux"
    const val AVANT_BRAS = "Avant-bras"
    const val TRAPEZES = "Trapèzes"
    const val LOMBAIRES = "Lombaires"
    const val ADDUCTEURS = "Adducteurs"

    const val BARRE = "Barre"
    const val HALTERES = "Haltères"
    const val POULIE = "Poulie"
    const val MACHINE = "Machine"
    const val POIDS_DU_CORPS = "Poids du corps"
    const val BARRE_EZ = "Barre EZ"
    const val SMITH = "Smith machine"
    const val KETTLEBELL = "Kettlebell"

    val all: List<CatalogExercise> = listOf(
        // ---- Pectoraux
        CatalogExercise("bench_press", "Développé couché", "Bench press", PECS, BARRE,
            listOf("developpe couche barre", "barbell bench press", "bench", "dc", "flat bench press", "developpe couche a la barre")),
        CatalogExercise("db_bench_press", "Développé couché haltères", "Dumbbell bench press", PECS, HALTERES,
            listOf("developpe halteres", "db bench press", "dumbbell press", "developpe couche aux halteres")),
        CatalogExercise("incline_bench_press", "Développé incliné", "Incline bench press", PECS, BARRE,
            listOf("developpe incline barre", "incline barbell press", "incline press", "di")),
        CatalogExercise("incline_db_press", "Développé incliné haltères", "Incline dumbbell press", PECS, HALTERES,
            listOf("incline db press", "developpe incline aux halteres", "dih", "incline dumbbell bench press")),
        CatalogExercise("decline_bench_press", "Développé décliné", "Decline bench press", PECS, BARRE,
            listOf("decline press", "developpe decline barre")),
        CatalogExercise("smith_incline_press", "Développé incliné à la Smith", "Smith machine incline press", PECS, SMITH,
            listOf("incline smith press", "developpe incline smith")),
        CatalogExercise("machine_chest_press", "Développé machine", "Machine chest press", PECS, MACHINE,
            listOf("chest press", "presse pectoraux", "developpe assis machine")),
        CatalogExercise("cable_fly", "Écarté à la poulie", "Cable fly", PECS, POULIE,
            listOf("cable crossover", "vis a vis", "ecarte poulie", "cable flyes", "crossover poulie")),
        CatalogExercise("db_fly", "Écarté couché haltères", "Dumbbell fly", PECS, HALTERES,
            listOf("ecarte halteres", "dumbbell flyes", "chest fly", "ecartes")),
        CatalogExercise("pec_deck", "Pec deck", "Pec deck", PECS, MACHINE,
            listOf("butterfly", "machine fly", "ecarte machine", "pec fly")),
        CatalogExercise("dips", "Dips", "Dips", PECS, POIDS_DU_CORPS,
            listOf("dips pectoraux", "chest dips", "dips lestes", "weighted dips")),
        CatalogExercise("push_up", "Pompes", "Push-up", PECS, POIDS_DU_CORPS,
            listOf("push ups", "pushup", "pompe")),

        // ---- Dos
        CatalogExercise("pull_up", "Tractions", "Pull-up", DOS, POIDS_DU_CORPS,
            listOf("pull ups", "pullup", "traction", "tractions pronation", "tractions lestees", "weighted pull ups")),
        CatalogExercise("chin_up", "Tractions supination", "Chin-up", DOS, POIDS_DU_CORPS,
            listOf("chin ups", "chinup", "tractions en supination")),
        CatalogExercise("lat_pulldown", "Tirage vertical", "Lat pulldown", DOS, POULIE,
            listOf("tirage poitrine", "pulldown", "lat pull down", "tirage haut", "tirage vertical poulie haute")),
        CatalogExercise("close_grip_pulldown", "Tirage vertical prise serrée", "Close-grip lat pulldown", DOS, POULIE,
            listOf("tirage vertical serre", "close grip pulldown", "neutral grip pulldown")),
        CatalogExercise("barbell_row", "Rowing barre", "Barbell row", DOS, BARRE,
            listOf("bent over row", "rowing buste penche", "rowing barre buste penche", "bb row", "pendlay row")),
        CatalogExercise("db_row", "Rowing haltère", "Dumbbell row", DOS, HALTERES,
            listOf("one arm dumbbell row", "rowing unilateral", "rowing haltere un bras", "db row", "kroc row")),
        CatalogExercise("seated_cable_row", "Tirage horizontal", "Seated cable row", DOS, POULIE,
            listOf("rowing assis poulie", "cable row", "tirage horizontal poulie", "seated row", "low row")),
        CatalogExercise("t_bar_row", "Rowing T-bar", "T-bar row", DOS, BARRE,
            listOf("tbar row", "rowing t bar")),
        CatalogExercise("chest_supported_row", "Rowing buste appuyé", "Chest-supported row", DOS, MACHINE,
            listOf("chest supported row", "rowing machine", "machine row", "seal row")),
        CatalogExercise("straight_arm_pulldown", "Pull-over poulie", "Straight-arm pulldown", DOS, POULIE,
            listOf("straight arm pulldown", "pullover poulie", "tirage bras tendus")),
        CatalogExercise("db_pullover", "Pull-over haltère", "Dumbbell pullover", DOS, HALTERES,
            listOf("pullover", "pull over")),
        CatalogExercise("deadlift", "Soulevé de terre", "Deadlift", DOS, BARRE,
            listOf("sdt", "conventional deadlift", "souleve de terre conventionnel")),
        CatalogExercise("rack_pull", "Rack pull", "Rack pull", DOS, BARRE, listOf("souleve de terre partiel")),
        CatalogExercise("back_extension", "Extension lombaire", "Back extension", LOMBAIRES, POIDS_DU_CORPS,
            listOf("hyperextension", "hyperextensions", "extensions lombaires", "banc a lombaires")),

        // ---- Épaules
        CatalogExercise("overhead_press", "Développé militaire", "Overhead press", EPAULES, BARRE,
            listOf("ohp", "military press", "developpe militaire barre", "standing press", "strict press")),
        CatalogExercise("db_shoulder_press", "Développé épaules haltères", "Dumbbell shoulder press", EPAULES, HALTERES,
            listOf("developpe assis halteres", "seated dumbbell press", "db shoulder press", "developpe halteres epaules")),
        CatalogExercise("machine_shoulder_press", "Développé épaules machine", "Machine shoulder press", EPAULES, MACHINE,
            listOf("shoulder press machine", "shoulder press")),
        CatalogExercise("arnold_press", "Développé Arnold", "Arnold press", EPAULES, HALTERES, listOf("arnold")),
        CatalogExercise("lateral_raise", "Élévations latérales", "Lateral raises", EPAULES, HALTERES,
            listOf("elevation laterale", "side raises", "lateral raise", "db lateral raise", "elevations laterales halteres")),
        CatalogExercise("cable_lateral_raise", "Élévations latérales à la poulie", "Cable lateral raises", EPAULES, POULIE,
            listOf("elevation laterale poulie", "cable lateral raise", "cable side raise")),
        CatalogExercise("machine_lateral_raise", "Élévations latérales machine", "Machine lateral raises", EPAULES, MACHINE,
            listOf("lateral raise machine")),
        CatalogExercise("front_raise", "Élévations frontales", "Front raises", EPAULES, HALTERES,
            listOf("elevation frontale", "front raise")),
        CatalogExercise("rear_delt_fly", "Oiseau", "Rear delt fly", EPAULES, HALTERES,
            listOf("oiseau halteres", "reverse fly", "rear delt raise", "bent over lateral raise", "ecarte arriere")),
        CatalogExercise("reverse_pec_deck", "Reverse pec deck", "Reverse pec deck", EPAULES, MACHINE,
            listOf("pec deck inverse", "rear delt machine", "reverse fly machine")),
        CatalogExercise("face_pull", "Face pull", "Face pull", EPAULES, POULIE, listOf("face pulls", "tirage visage")),
        CatalogExercise("upright_row", "Tirage menton", "Upright row", EPAULES, BARRE, listOf("rowing menton")),
        CatalogExercise("shrug", "Shrugs", "Shrugs", TRAPEZES, HALTERES,
            listOf("haussements d epaules", "shrug", "barbell shrug", "dumbbell shrug")),

        // ---- Biceps
        CatalogExercise("barbell_curl", "Curl barre", "Barbell curl", BICEPS, BARRE,
            listOf("curl a la barre", "bb curl", "biceps curl barre")),
        CatalogExercise("ez_curl", "Curl barre EZ", "EZ-bar curl", BICEPS, BARRE_EZ, listOf("ez bar curl", "curl ez")),
        CatalogExercise("db_curl", "Curl haltères", "Dumbbell curl", BICEPS, HALTERES,
            listOf("curl halteres", "biceps curl", "db curl", "curl biceps", "alternate curl", "curl alterne")),
        CatalogExercise("hammer_curl", "Curl marteau", "Hammer curl", BICEPS, HALTERES,
            listOf("hammer curls", "curl prise marteau")),
        CatalogExercise("incline_db_curl", "Curl incliné", "Incline dumbbell curl", BICEPS, HALTERES,
            listOf("incline curl", "curl incline halteres")),
        CatalogExercise("preacher_curl", "Curl pupitre", "Preacher curl", BICEPS, BARRE_EZ,
            listOf("larry scott", "curl larry scott", "preacher curls")),
        CatalogExercise("cable_curl", "Curl poulie", "Cable curl", BICEPS, POULIE, listOf("curl a la poulie", "cable biceps curl")),
        CatalogExercise("concentration_curl", "Curl concentré", "Concentration curl", BICEPS, HALTERES, listOf("curl concentration")),
        CatalogExercise("bayesian_curl", "Curl bayésien", "Bayesian curl", BICEPS, POULIE, listOf("bayesian cable curl")),

        // ---- Triceps
        CatalogExercise("triceps_pushdown", "Extension triceps poulie", "Triceps pushdown", TRICEPS, POULIE,
            listOf("pushdown", "triceps poulie haute", "extension triceps a la poulie", "rope pushdown",
                "cable triceps extension", "push down", "pushdown corde", "extensions triceps poulie")),
        CatalogExercise("overhead_triceps_extension", "Extension triceps au-dessus de la tête", "Overhead triceps extension", TRICEPS, POULIE,
            listOf("overhead extension", "extension nuque", "overhead cable extension", "extension triceps derriere la tete")),
        CatalogExercise("skull_crusher", "Barre au front", "Skull crusher", TRICEPS, BARRE_EZ,
            listOf("skullcrusher", "skull crushers", "lying triceps extension", "extension couche barre")),
        CatalogExercise("close_grip_bench", "Développé couché prise serrée", "Close-grip bench press", TRICEPS, BARRE,
            listOf("close grip bench", "cgbp", "developpe serre")),
        CatalogExercise("triceps_dips", "Dips triceps", "Triceps dips", TRICEPS, POIDS_DU_CORPS, listOf("bench dips", "dips banc")),
        CatalogExercise("triceps_kickback", "Kickback triceps", "Triceps kickback", TRICEPS, HALTERES, listOf("kickback", "kick back")),

        // ---- Jambes
        CatalogExercise("squat", "Squat", "Squat", QUADRICEPS, BARRE,
            listOf("back squat", "squat barre", "barbell squat", "high bar squat", "low bar squat")),
        CatalogExercise("front_squat", "Squat avant", "Front squat", QUADRICEPS, BARRE, listOf("front squat barre")),
        CatalogExercise("hack_squat", "Hack squat", "Hack squat", QUADRICEPS, MACHINE, listOf("hack squat machine")),
        CatalogExercise("smith_squat", "Squat à la Smith", "Smith machine squat", QUADRICEPS, SMITH, listOf("smith squat")),
        CatalogExercise("leg_press", "Presse à cuisses", "Leg press", QUADRICEPS, MACHINE,
            listOf("presse", "presse inclinee", "leg press 45", "presse a cuisse", "presse 45")),
        CatalogExercise("leg_extension", "Leg extension", "Leg extension", QUADRICEPS, MACHINE,
            listOf("extension jambes", "leg extensions", "extension quadriceps", "quad extension")),
        CatalogExercise("bulgarian_split_squat", "Squat bulgare", "Bulgarian split squat", QUADRICEPS, HALTERES,
            listOf("fente bulgare", "split squat bulgare", "bss", "bulgarian squat")),
        CatalogExercise("lunge", "Fentes", "Lunges", QUADRICEPS, HALTERES,
            listOf("fente", "walking lunges", "fentes marchees", "lunge", "fentes avant")),
        CatalogExercise("goblet_squat", "Goblet squat", "Goblet squat", QUADRICEPS, HALTERES, listOf("squat goblet")),
        CatalogExercise("romanian_deadlift", "Soulevé de terre roumain", "Romanian deadlift", ISCHIOS, BARRE,
            listOf("rdl", "sdt roumain", "souleve de terre jambes tendues", "stiff leg deadlift", "sldl", "jambes tendues")),
        CatalogExercise("lying_leg_curl", "Leg curl allongé", "Lying leg curl", ISCHIOS, MACHINE,
            listOf("leg curl couche", "lying leg curls", "leg curl allonge")),
        CatalogExercise("seated_leg_curl", "Leg curl assis", "Seated leg curl", ISCHIOS, MACHINE,
            listOf("seated leg curls", "leg curl assis machine")),
        CatalogExercise("leg_curl", "Leg curl", "Leg curl", ISCHIOS, MACHINE, listOf("leg curls", "curl jambes", "flexion jambes")),
        CatalogExercise("nordic_curl", "Nordic curl", "Nordic hamstring curl", ISCHIOS, POIDS_DU_CORPS, listOf("nordic", "nordic curls")),
        CatalogExercise("hip_thrust", "Hip thrust", "Hip thrust", FESSIERS, BARRE,
            listOf("hip thrusts", "releve de bassin", "barbell hip thrust", "pont fessier leste")),
        CatalogExercise("glute_bridge", "Pont fessier", "Glute bridge", FESSIERS, POIDS_DU_CORPS, listOf("glute bridges")),
        CatalogExercise("hip_abduction", "Abduction hanche", "Hip abduction", FESSIERS, MACHINE,
            listOf("abducteurs machine", "abduction machine", "hip abductor")),
        CatalogExercise("hip_adduction", "Adduction hanche", "Hip adduction", ADDUCTEURS, MACHINE,
            listOf("adducteurs machine", "adduction machine", "hip adductor")),
        CatalogExercise("cable_kickback", "Kickback fessier poulie", "Cable glute kickback", FESSIERS, POULIE,
            listOf("glute kickback", "kickback fessiers")),
        CatalogExercise("standing_calf_raise", "Mollets debout", "Standing calf raises", MOLLETS, MACHINE,
            listOf("calf raises", "calf raise", "extension mollets", "mollets", "standing calf raise", "extensions mollets debout")),
        CatalogExercise("seated_calf_raise", "Mollets assis", "Seated calf raises", MOLLETS, MACHINE,
            listOf("seated calf raise", "extension mollets assis")),
        CatalogExercise("leg_press_calf_raise", "Mollets à la presse", "Leg press calf raises", MOLLETS, MACHINE,
            listOf("calf press", "mollets presse")),

        // ---- Abdominaux / gainage
        CatalogExercise("crunch", "Crunch", "Crunch", ABDOS, POIDS_DU_CORPS, listOf("crunchs", "crunches")),
        CatalogExercise("cable_crunch", "Crunch à la poulie", "Cable crunch", ABDOS, POULIE, listOf("crunch poulie", "cable crunches")),
        CatalogExercise("hanging_leg_raise", "Relevé de jambes suspendu", "Hanging leg raise", ABDOS, POIDS_DU_CORPS,
            listOf("releve de jambes", "leg raises", "hanging leg raises", "releves de jambes")),
        CatalogExercise("plank", "Gainage", "Plank", ABDOS, POIDS_DU_CORPS, listOf("planche", "gainage ventral")),
        CatalogExercise("ab_wheel", "Roue abdominale", "Ab wheel rollout", ABDOS, POIDS_DU_CORPS, listOf("ab rollout", "roulette abdos")),

        // ---- Avant-bras
        CatalogExercise("wrist_curl", "Curl poignets", "Wrist curl", AVANT_BRAS, BARRE, listOf("wrist curls", "flexion poignets")),
        CatalogExercise("farmer_walk", "Marche du fermier", "Farmer's walk", AVANT_BRAS, HALTERES, listOf("farmer walk", "farmers carry")),
    )

    private val byKey = all.associateBy { it.key }

    fun byKey(key: String): CatalogExercise? = byKey[key]

    /** Toutes les formes connues (normalisées) pour un exercice. */
    fun aliases(ex: CatalogExercise): List<String> =
        (listOf(ex.nameFr, ex.nameEn) + ex.synonyms).map { TextNormalizer.normalizeName(it) }.distinct()
}
