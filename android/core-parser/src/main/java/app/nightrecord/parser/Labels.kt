package app.nightrecord.parser

object Labels {
    val STAGE = mapOf(
        1 to "중단",
        2 to "1일차",
        3 to "2일차",
        6 to "최종일",
    )

    val JOB = mapOf(
        1 to ("추적자" to "Wylder"),
        2 to ("수호자" to "Guardian"),
        3 to ("철의 눈" to "Ironeye"),
        4 to ("레이디" to "Duchess"),
        5 to ("무뢰한" to "Raider"),
        6 to ("복수자" to "Revenant"),
        7 to ("은둔자" to "Recluse"),
        8 to ("집행자" to "Executor"),
        9 to ("학자" to "Scholar"),
        10 to ("장의사" to "Undertaker"),
    )

    val BOSS = mapOf(
        0 to ("세목 달린 짐승" to "Gladius, Beast of Night"),
        1 to ("달려드는 턱" to "Adel, Baron of Night"),
        2 to ("지성의 벌레" to "Gnoster, Wisdom of Night"),
        3 to ("징조" to "Maris, Fathom of Night"),
        4 to ("조율의 마물" to "Libra, Creature of Night"),
        5 to ("어둠을 달리는 사냥꾼" to "Fulghor, Champion of Nightglow"),
        6 to ("안개의 틈새" to "Caligo, Miasma of Night"),
        7 to ("밤을 본뜬 자" to "Heolstor, the Nightlord"),
        8 to ("안녕을 가져오는 자들" to "Weapon-Bequeathed Harmonia"),
        9 to ("잔해의 왕" to "Traitorous Straghess"),
    )

    val MODE = mapOf(
        "normal" to "일반",
        "deep" to "심도",
        "unknown" to "미확인",
    )

    fun jobKo(id: Int) = JOB[id]?.first ?: "직업 $id"
    fun jobEn(id: Int) = JOB[id]?.second ?: ""
    fun bossKo(id: Int) = BOSS[id]?.first ?: "미확인 보스 (ID $id)"
    fun bossEn(id: Int) = BOSS[id]?.second ?: ""
    fun mode(mode: String) = MODE[mode] ?: mode
    fun stage(type: Int?) = if (type == null) "—" else STAGE[type] ?: "기타"
}
