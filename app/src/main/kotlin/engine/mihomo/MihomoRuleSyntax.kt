// Copyright 2026, AsteriskMETA contributors
// SPDX-License-Identifier: GPL-3.0

package engine.mihomo

import engine.network.isCidrAddress

/**
 * The rule types the form can edit. Everything else, and every rule whose parameters cannot
 * be represented without loss, is edited as raw text.
 */
internal enum class MihomoFormRuleType(
    val keyword: String,
    val supportsIpParams: Boolean = false,
    val acceptsCommaPayload: Boolean = false,
) {
    Domain("DOMAIN"),
    DomainSuffix("DOMAIN-SUFFIX"),
    DomainKeyword("DOMAIN-KEYWORD"),
    DomainRegex("DOMAIN-REGEX", acceptsCommaPayload = true),
    DomainWildcard("DOMAIN-WILDCARD"),
    IpCidr("IP-CIDR", supportsIpParams = true),
    IpCidr6("IP-CIDR6", supportsIpParams = true),
    IpSuffix("IP-SUFFIX", supportsIpParams = true),
    IpAsn("IP-ASN", supportsIpParams = true),
    GeoIp("GEOIP", supportsIpParams = true),
    SrcGeoIp("SRC-GEOIP"),
    SrcIpAsn("SRC-IP-ASN"),
    SrcIpCidr("SRC-IP-CIDR"),
    SrcIpSuffix("SRC-IP-SUFFIX"),
    GeoSite("GEOSITE"),
    DstPort("DST-PORT"),
    SrcPort("SRC-PORT"),
    InPort("IN-PORT"),
    InType("IN-TYPE"),
    InUser("IN-USER"),
    InName("IN-NAME"),
    RematchName("REMATCH-NAME"),
    Network("NETWORK"),
    ProcessName("PROCESS-NAME"),
    ProcessPath("PROCESS-PATH"),
    ProcessNameRegex("PROCESS-NAME-REGEX", acceptsCommaPayload = true),
    ProcessPathRegex("PROCESS-PATH-REGEX", acceptsCommaPayload = true),
    ProcessNameWildcard("PROCESS-NAME-WILDCARD"),
    ProcessPathWildcard("PROCESS-PATH-WILDCARD"),
    Uid("UID"),
    Dscp("DSCP"),
    RuleSet("RULE-SET", supportsIpParams = true),
    And("AND", acceptsCommaPayload = true),
    Or("OR", acceptsCommaPayload = true),
    Not("NOT", acceptsCommaPayload = true),
}

internal data class MihomoFormRule(
    val type: MihomoFormRuleType,
    val payload: String,
    val target: String,
    val noResolve: Boolean = false,
    val src: Boolean = false,
) {
    fun toRawRule(): String = buildString {
        append(type.keyword).append(',').append(payload.trim()).append(',').append(target.trim())
        if (src && type.supportsIpParams) append(',').append(SourceIpParameter)
        if (noResolve && type.supportsIpParams) append(',').append(NoResolveParameter)
    }
}

internal enum class MihomoRuleInputError {
    Blank,
    MultipleLines,
    YamlListSyntax,
    MissingFields,
    PayloadRequired,
    TargetRequired,
    InvalidCidr,
    InvalidPort,
    InvalidUid,
    InvalidDscp,
    InvalidNetwork,
    /** A comma would end the field early; such a rule has to be written as raw text. */
    CommaInField,
}

/** The form representation of [this] rule, or `null` when only raw text can hold it. */
internal fun String.toMihomoFormRuleOrNull(): MihomoFormRule? {
    val tokens = trim().split(',').map(String::trim)
    val type = MihomoFormRuleType.entries.firstOrNull { type -> type.keyword == tokens.first().uppercase() }
        ?: return null
    if (type.acceptsCommaPayload) {
        // Regex and logical rules have no parameters; the policy is the last field.
        if (tokens.size < 3) return null
        val payload = trim().substringAfter(',').substringBeforeLast(',').trim()
        return MihomoFormRule(type, payload, tokens.last()).takeIf { rule ->
            rule.payload.isNotEmpty() && rule.target.isNotEmpty()
        }
    }
    if (tokens.size < 3) return null
    if (tokens[1].isEmpty() || tokens[2].isEmpty()) return null
    val params = tokens.drop(3).map(String::lowercase)
    if (params.isNotEmpty() && !type.supportsIpParams) return null
    if (params.any { it != NoResolveParameter && it != SourceIpParameter } || params.distinct().size != params.size) return null
    return MihomoFormRule(
        type, tokens[1], tokens[2],
        noResolve = NoResolveParameter in params,
        src = SourceIpParameter in params,
    )
}

/** The first problem of a form rule, checked only as far as the form can know. */
internal fun MihomoFormRule.inputError(): MihomoRuleInputError? {
    val payload = payload.trim()
    val target = target.trim()
    return when {
        payload.isEmpty() -> MihomoRuleInputError.PayloadRequired
        target.isEmpty() -> MihomoRuleInputError.TargetRequired
        '\n' in payload || '\r' in payload || '\n' in target || '\r' in target -> MihomoRuleInputError.MultipleLines
        ',' in target || !type.acceptsCommaPayload && ',' in payload -> MihomoRuleInputError.CommaInField
        type in CidrRuleTypes ->
            MihomoRuleInputError.InvalidCidr.takeUnless { isCidrAddress(payload) }
        type in PortRuleTypes ->
            MihomoRuleInputError.InvalidPort.takeUnless { payload.isMihomoUnsignedRanges(65535L) }
        type == MihomoFormRuleType.Uid ->
            MihomoRuleInputError.InvalidUid.takeUnless { payload.isMihomoUnsignedRanges(4294967295L) }
        type == MihomoFormRuleType.Dscp ->
            MihomoRuleInputError.InvalidDscp.takeUnless { payload.isMihomoUnsignedRanges(63L) }
        type == MihomoFormRuleType.Network ->
            MihomoRuleInputError.InvalidNetwork.takeUnless { payload.uppercase() in MihomoRuleNetworks }
        else -> null
    }
}

/** The structural problem of a raw rule. The core decides whether its content is valid. */
internal fun String.mihomoRawRuleInputError(): MihomoRuleInputError? {
    val rule = trim()
    return when {
        rule.isEmpty() -> MihomoRuleInputError.Blank
        '\n' in rule || '\r' in rule -> MihomoRuleInputError.MultipleLines
        rule.looksLikeYamlRuleLine() -> MihomoRuleInputError.YamlListSyntax
        ',' !in rule || rule.substringAfter(',').isBlank() -> MihomoRuleInputError.MissingFields
        else -> null
    }
}

/** Whether the rule's type is one this app knows; unknown types are kept and left to the core. */
internal fun String.hasKnownMihomoRuleType(): Boolean = mihomoRuleType() in KnownMihomoRuleTypes

internal class MihomoRuleImport(
    val rules: List<String>,
    /** One-based line number to the problem of that line. */
    val errors: List<Pair<Int, MihomoRuleInputError>>,
)

/**
 * Reads pasted text as one bare rule per line. Blank lines and whole-line `#` comments are
 * skipped; a line in YAML list syntax is an error rather than silently unwrapped, because
 * pasted YAML usually carries more than rules.
 */
internal fun String.parseMihomoRuleImport(): MihomoRuleImport {
    val rules = mutableListOf<String>()
    val errors = mutableListOf<Pair<Int, MihomoRuleInputError>>()
    lineSequence().forEachIndexed { index, line ->
        val rule = line.trim()
        if (rule.isEmpty() || rule.startsWith("#")) return@forEachIndexed
        val error = rule.mihomoRawRuleInputError()
        if (error == null) rules += rule else errors += (index + 1) to error
    }
    return MihomoRuleImport(rules, errors)
}

/** What a rule editor offers and shows about the source configuration. */
internal data class MihomoRuleSourceInfo(
    val rules: List<String> = emptyList(),
    /** Built-in policies, then policy groups, then static proxies. */
    val targets: List<String> = MihomoBuiltInRuleTargets,
    val ruleProviders: List<String> = emptyList(),
    /** The policy of the first `MATCH`, when the source has one. */
    val matchTarget: String? = null,
)

internal fun Map<String, Any?>.toMihomoRuleSourceInfo(): MihomoRuleSourceInfo {
    val rules = mihomoSourceRules()
    return MihomoRuleSourceInfo(
        rules = rules,
        targets = (MihomoBuiltInRuleTargets + namesOf("proxy-groups") + namesOf("proxies")).distinct(),
        ruleProviders = (this["rule-providers"] as? Map<*, *>)?.keys?.filterIsInstance<String>().orEmpty(),
        matchTarget = rules.firstOrNull(String::isMihomoMatchRule)?.substringAfter(',', "")?.trim()?.takeIf(String::isNotEmpty),
    )
}

/** A rule of the compiled configuration that names something the configuration does not define. */
internal data class MihomoRuleReferenceIssue(
    val rule: String,
    val missingName: String,
    val isRuleProvider: Boolean,
)

/**
 * Checks the references of the rules this app can read reliably: plain rules and `MATCH`.
 * Comma-bearing rules use their final field as the policy. `SUB-RULE` and unknown types are
 * skipped. It is a hint only; the core has the
 * final say.
 */
internal fun Map<String, Any?>.mihomoRuleReferenceIssues(limit: Int = 20): List<MihomoRuleReferenceIssue> {
    val info = toMihomoRuleSourceInfo()
    val targets = info.targets.toHashSet()
    val providers = info.ruleProviders.toHashSet()
    val issues = mutableListOf<MihomoRuleReferenceIssue>()
    for (rule in info.rules) {
        if (issues.size >= limit) break
        val tokens = rule.split(',').map(String::trim)
        val type = tokens.first().uppercase()
        val target = when {
            type == MihomoMatchRuleType -> tokens.getOrNull(1)
            type in CommaPayloadRuleTypes -> tokens.last().takeIf { tokens.size >= 3 }
            type in PlainMihomoRuleTypes -> tokens.getOrNull(2)
            else -> null
        } ?: continue
        if (type == MihomoFormRuleType.RuleSet.keyword && tokens[1] !in providers) {
            issues += MihomoRuleReferenceIssue(rule, tokens[1], isRuleProvider = true)
        }
        if (target !in targets) issues += MihomoRuleReferenceIssue(rule, target, isRuleProvider = false)
    }
    return issues
}

private fun Map<String, Any?>.namesOf(key: String): List<String> =
    (this[key] as? List<*>).orEmpty().mapNotNull { item -> (item as? Map<*, *>)?.get("name") as? String }

private fun String.looksLikeYamlRuleLine(): Boolean =
    startsWith("- ") || startsWith("-\t") || equals("rules:", ignoreCase = true) || startsWith("rules:")

private fun String.isMihomoUnsignedRanges(max: Long): Boolean = split('/').all { part ->
    val bounds = part.split('-').map(String::trim)
    if (bounds.size !in 1..2) return@all false
    val numbers = bounds.map { bound ->
        if (bound.isEmpty() || bound.any { it !in '0'..'9' }) return@all false
        bound.toLongOrNull()?.takeIf { it in 0L..max } ?: return@all false
    }
    numbers.first() <= numbers.last()
}

private const val NoResolveParameter = "no-resolve"
private const val SourceIpParameter = "src"

internal val MihomoRuleNetworks = listOf("TCP", "UDP")
internal val MihomoBuiltInRuleTargets = listOf("DIRECT", "REJECT", "REJECT-DROP", "PASS", "PASS-RULE", "COMPATIBLE", "GLOBAL")

private val CidrRuleTypes = setOf(MihomoFormRuleType.IpCidr, MihomoFormRuleType.IpCidr6, MihomoFormRuleType.SrcIpCidr)
private val PortRuleTypes = setOf(MihomoFormRuleType.DstPort, MihomoFormRuleType.SrcPort, MihomoFormRuleType.InPort)
private val PlainMihomoRuleTypes = MihomoFormRuleType.entries.mapTo(hashSetOf()) { type -> type.keyword }
private val CommaPayloadRuleTypes = MihomoFormRuleType.entries.filter(MihomoFormRuleType::acceptsCommaPayload)
    .mapTo(hashSetOf(), MihomoFormRuleType::keyword)

private val KnownMihomoRuleTypes = PlainMihomoRuleTypes + setOf(
    "SUB-RULE", MihomoMatchRuleType,
)
