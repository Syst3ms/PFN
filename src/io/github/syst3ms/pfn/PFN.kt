package io.github.syst3ms.pfn

import java.math.BigInteger
import kotlin.math.max
import kotlin.system.exitProcess

val INPUT_PATTERN = "(\\d+)\\[(.+)]".toRegex()

fun main() {
    println("Note : if at any point you wish to quit the program, simply type \"exit\" in place of any text")
    println("Please enter an array of the form a[#]")
    println("  where a is the base and # is the array.")
    println("  Use 0 as the base to get a controlled expansion that might not grow very large.")
    println()
    var match: MatchResult?
    do {
        val input = readInput()
        match = INPUT_PATTERN.matchEntire(input)
        if (match == null) {
            println("Your input is invalid, please try again :")
            println()
        }
    } while (match == null)
    val base = match.groupValues[1].toBigInteger()
    val array = parseArray(match.groupValues[2])
    println("Do you want to expand the array one step at a time (enter 0),")
    println("  or do you want to compute the final result (enter 1) ?")
    println("  (the latter can quickly become impossible for any computer, so the program might hang)")
    println()
    var step: Boolean?
    do {
        val i = readInput().toIntOrNull()
        step = if (i == 0) true else if (i == 1 && base.signum() > 0) false else null
        if (step == null) {
            if (i == 1)
                println("Controlled expansion is only available (and interesting) in conjunction with step mode.")
            println("Please enter 0 or 1 :")
            println()
        }
    } while (step == null)
    if (step) {
        println("Each expansion step will be displayed one after the other. Simply press enter when you want to see the next step.")
        println()
        var current = base to array.clean()
        do {
            print(current.first.toString() + "[" + current.second.arrayToString() + "]")
            readInput()
            val (b, a) = current.second.step(current.first)
            current = b to a.clean()
        } while (current.second.size() > 0)
        println(current.first.toString())
        println()
        println("Expansion complete!")
        exitProcess(0)
    } else {
        println("Result :")
        println(array.compute(base))
    }
}

fun readInput() : String {
    val text = readLine()!!
    if (text.equals("exit", ignoreCase = true)) {
        exitProcess(0)
    } else {
        return text
    }
}

fun PFNArray.compute(base: BigInteger): BigInteger {
    var array = this
    var result = base
    while (array.size() > 0) {
        val (r, a) = array.step(result)
        array = a.clean()
        result = r
    }
    return result
}

fun PFNArray.step(base: BigInteger): Pair<BigInteger, PFNArray> {
    assert(this.size() > 0)
    if (this.size() == 1 && this[0].isZero()) {
        return (if (base == BigInteger.ZERO) BigInteger.ZERO else base.add(BigInteger.ONE)) to empty()
    }
    val reconstructionArray = arrayListOf<ReconstructionEntry>()
    val allArrays = arrayListOf(this)
    val allSeparators = arrayListOf(this)
    var currentArray = this.duplicate()
    var currentIndex = 0
    var ad = 1
    var rad = 1
    var sd = 1
    var rsd = 1
    while (currentIndex < currentArray.size()) {
        var entry = currentArray[currentIndex]
        if (entry.isZero()) {
            rad = 0
            rsd = 0
            currentIndex += 2
        } else if (entry is NestedArray && entry.array.size() == 1 && entry.array[0].isZero()) {
            reconstructionArray.add(ReconstructionEntry(currentArray, currentIndex, null, ad - 1))
            reconstructionArray.add(ReconstructionEntry(NumberElement(if (base == BigInteger.ZERO && (sd < 2 && ad < 2)) 2 else if (base == BigInteger.ZERO) 1 else base.toInt()).wrap(), -1, null, ad))
            return base to reconstructFullArray(reconstructionArray)
        } else if (entry is NestedArray) {
            reconstructionArray.add(ReconstructionEntry(currentArray, currentIndex, true, ad - 1))
            allArrays.add(entry.array)
            ad++
            rad++
            currentArray = entry.array
            currentIndex = 0
        } else if (entry is NestedBrace) {
            if (sd == 1)
                throw IllegalStateException("Found brace entry on the first level")
            var stop = false
            do {
                if (entry.isZero(strict = false)) {
                    rad = 0
                    rsd = 0
                    currentIndex += 2
                    entry = currentArray[currentIndex]
                } else {
                    val workingPFN = allSeparators[sd - rsd - 1]
                    val workingArray = workingPFN.flat()
                    val workingIndex = reconstructionArray.find { it.globalIndex == sd - rsd - 1 }?.acceptIndex ?: currentIndex
                    val depths = workingArray.map(ArrayElement::braceDepth)
                            .filterIndexed { i, _ -> i % 2 == 0 }
                    val maxDepth = depths.max()!!
                    val minDepth = depths.min()!!
                    val m = depths.indexOf(maxDepth) * 2
                    var c = entry.firstNumber().value
                    var p = entry.originalString.substringBefore(c.toString())
                    var q = entry.originalString.substringAfter(c.toString())
                    if (c == 0) {
                        val newArray = (entry as NestedBrace).array
                        reconstructionArray.add(ReconstructionEntry(workingPFN, workingIndex, false, sd - 1))
                        allSeparators.add(newArray)
                        sd++
                        rsd++
                        currentArray = newArray
                        currentIndex = 0
                        stop = true
                    } else if (maxDepth != minDepth || workingIndex == 0 && c == 1 && p.isEmpty() && q.isEmpty()) {
                        if (maxDepth == 0 && minDepth == 0)
                            throw IllegalStateException("Illegal braces inside of nested array")
                        val dominant = (workingArray[m] as NestedBrace).peel()
                        c = dominant.firstNumber().value
                        p = dominant.originalString.substringBefore(c.toString())
                        q = dominant.originalString.substringAfter(c.toString())
                        val x = workingArray.copyOfRange(0, m).joinToString("")
                        val y = workingArray.copyOfRange(m+1, workingArray.size).joinToString("")
                        fun nest(n: BigInteger): PFNArray {
                            var result = "0"
                            var i = BigInteger.ONE
                            while (i <= n) {
                                result = x + ("0{$result}1," + (p + max(c-1, 0) + q).encloseBraces(maxDepth)).encloseBraces(maxDepth - 1) + y
                                i = i.add(BigInteger.ONE)
                            }
                            return parseArray(result)
                        }
                        reconstructionArray.replaceOrAdd(sd - rsd - 1, nest(if (base == BigInteger.ZERO && sd <= 2) 2.toBigInteger() else if (base == BigInteger.ZERO) BigInteger.ONE else base), brackets = false)
                        reconstructionArray.removeIf { it.globalIndex > sd - rsd - 1 }
                        return base to reconstructFullArray(reconstructionArray)
                    } else {
                        val peeledEntry = (entry as NestedBrace).peel()
                        val d  = peeledEntry.firstNumber().value
                        val r = peeledEntry.originalString.substringBefore(d.toString())
                        val s = peeledEntry.originalString.substringAfter(d.toString())
                        val workingEntry = workingArray[workingIndex].toString()
                        val y = workingEntry.substringBefore(peeledEntry.originalString).dropLast(maxDepth)
                        val z = workingEntry.substringAfter(peeledEntry.originalString).drop(maxDepth) + workingArray.copyOfRange(workingIndex + 1, workingArray.size).joinToString("")
                        val previousSeparator = workingArray[workingIndex - 1] as NestedBrace
                        c = previousSeparator.firstNumber().value
                        p = previousSeparator.originalString.substringBefore(c.toString()) // originalString because the braces are ignored
                        q = previousSeparator.originalString.substringAfter(c.toString())
                        val x = workingArray.copyOfRange(0, workingIndex - 2).joinToString("")
                        if (p.isEmpty() && c == 0 && q.isEmpty()) {
                            if (maxDepth == 0 && minDepth == 0)
                                throw IllegalStateException("Illegal braces inside of nested array")
                            fun nest(n: BigInteger): PFNArray {
                                var result = "1"
                                var i = BigInteger.ONE
                                while (i <= n) {
                                    result = x + result.encloseBraces(maxDepth) + ",$y" + "$r${d-1}$s".encloseBraces(maxDepth) + z
                                    i = i.add(BigInteger.ONE)
                                }
                                return parseArray(result)
                            }
                            reconstructionArray.replaceOrAdd(sd - rsd - 1, nest(if (base == BigInteger.ZERO && sd <= 2) 2.toBigInteger() else if (base == BigInteger.ZERO) BigInteger.ONE else base))
                            reconstructionArray.removeIf { it.globalIndex > sd - rsd - 1 }
                            return base to reconstructFullArray(reconstructionArray)
                        } else if (c > 0 && (p.isEmpty() || c != 1 || p != q.replace('}', '{'))) {
                            if (maxDepth == 0 && minDepth == 0)
                                throw IllegalStateException("Illegal braces inside of nested array")
                            fun nest(n: BigInteger): PFNArray {
                                var result = "1".encloseBraces(maxDepth) + "{$p$c$q}$y" + "$r${d-1}$s".encloseBraces(maxDepth) + z
                                var i = BigInteger.ONE
                                while (i <= n) {
                                    result = "0".encloseBraces(maxDepth) + "{$p${c-1}$q}$result"
                                    i = i.add(BigInteger.ONE)
                                }
                                return parseArray("$x$result")
                            }
                            reconstructionArray.replaceOrAdd(sd - rsd - 1, nest(if (base == BigInteger.ZERO && sd <= 2) 2.toBigInteger() else if (base == BigInteger.ZERO) BigInteger.ONE else base))
                            reconstructionArray.removeIf { it.globalIndex > sd - rsd - 1 }
                            return base to reconstructFullArray(reconstructionArray)
                        } else {
                            val sepValue = parseArray(x + "0".encloseBraces(maxDepth) + "{$p$c$q}" + "1".encloseBraces(maxDepth) + "{$p$c$q}" + "$r${d-1}$s".encloseBraces(maxDepth) + z)
                            reconstructionArray.replaceOrAdd(sd - rsd - 1, sepValue, workingIndex - 1, false)
                            reconstructionArray.removeIf { it.globalIndex > sd - rsd - 1 }
                            allSeparators[sd - rsd - 1] = sepValue
                            allSeparators.add(previousSeparator.array)
                            sd++
                            if (c == 0) { // The (1)@n case
                                rsd++
                            }
                            currentArray = previousSeparator.array
                            currentIndex = 0
                            stop = true
                        }
                    }
                }
            } while (!stop)
        } else if (entry is NumberElement && entry.value > 0) {
            val b = entry.value
            if (ad == rad) {
                reconstructionArray.add(ReconstructionEntry(currentArray, currentIndex, null, ad - 1))
                reconstructionArray.add(ReconstructionEntry(arrayOf<ArrayElement>(NumberElement(b-1)) to emptyArray(), -1, null, ad))
                val full = reconstructFullArray(reconstructionArray)
                if (base == BigInteger.ZERO) {
                    return base to full
                }
                var newBase = base
                var i = BigInteger.ONE
                while (i < base) {
                    newBase = full.compute(newBase)
                    i = i.add(BigInteger.ONE)
                }
                return newBase to full
            }
            var workingArray = allArrays[ad - rad - 1].flat()
            var workingIndex = reconstructionArray.find { it.globalIndex == ad - rad - 1 }?.acceptIndex ?: currentIndex
            if (workingIndex % 2 != 0) {
                workingArray = currentArray.flat()
                workingIndex = currentIndex
            }
            val workingArrayEntry = workingArray[workingIndex].toString()
            var x = workingArray.copyOfRange(0, workingIndex - 2).joinToString("")
            val y = workingArrayEntry.substringBefore(b.toString())
            var z = workingArrayEntry.substringAfter(b.toString()) + workingArray.copyOfRange(workingIndex + 1, workingArray.size).joinToString("")
            for (rec in reconstructionArray.asReversed()) {
                if (rec.brackets != false || rec.acceptIndex % 2 == 0 || rec.globalIndex < ad - rad - 1)
                    break
                val intermediate = rec.array.flat()
                x = intermediate.copyOfRange(0, rec.acceptIndex).joinToString("") + "{$x"
                z += "}" + intermediate.copyOfRange(rec.acceptIndex + 1, intermediate.size).joinToString("")
            }
            val previousSeparator = workingArray[workingIndex - 1] as NestedBrace
            val c = previousSeparator.firstNumber().value
            val p = previousSeparator.originalString.substringBefore(c.toString()) // originalString because the braces are ignored
            val q = previousSeparator.originalString.substringAfter(c.toString())
            if (c == 0 && p.isEmpty() && q.isEmpty()) {
                fun nest(n: BigInteger): PFNArray {
                    var result = "0"
                    var j = BigInteger.ONE
                    while (j <= n) {
                        result = "[$x$result,$y${b-1}$z]"
                        j = j.add(BigInteger.ONE)
                    }
                    return parseArray("$x$result,$y${b-1}$z")
                }
                reconstructionArray.replaceOrAdd(ad - rad - 1, nest(if (base == BigInteger.ZERO && (sd < 2 && ad < 2)) 2.toBigInteger() else if (base == BigInteger.ZERO) BigInteger.ONE else base), brackets = true)
                reconstructionArray.removeIf { it.globalIndex > ad - rad - 1 }
                return base to reconstructFullArray(reconstructionArray)
            } else if (c > 0 && (p.isEmpty() || c != 1 || p != q.replace('}', '{'))) {
                fun nest(n: BigInteger): PFNArray {
                    var result = "1{$p$c$q}$y${b-1}$z"
                    var j = BigInteger.ONE
                    while (j <= n) {
                        result = "0{$p${c-1}$q}$result"
                        j = j.add(BigInteger.ONE)
                    }
                    return parseArray("$x$result")
                }
                reconstructionArray.replaceOrAdd(ad - rad - 1, nest(if (base == BigInteger.ZERO && sd < 2) 2.toBigInteger() else if (base == BigInteger.ZERO) BigInteger.ONE else base), brackets = true)
                reconstructionArray.removeIf { it.globalIndex > ad - rad - 1 }
                return base to reconstructFullArray(reconstructionArray)
            } else {
                val arrayValue = parseArray(x + "0{$p$c$q}1{$p$c$q}$y${b-1}$z")
                reconstructionArray.replaceOrAdd(ad - rad - 1, arrayValue, workingIndex - 1, false)
                reconstructionArray.removeIf { it.globalIndex > ad - rad - 1 }
                allArrays[ad - rad - 1] = arrayValue
                allSeparators.add(previousSeparator.array)
                sd++
                if (c == 0) { // The (1)@n case
                    rsd++
                }
                currentArray = previousSeparator.array
                currentIndex = 0
            }
        } else {
            throw IllegalStateException()
        }
    }
    throw IllegalStateException()
}

typealias PFNArray = Pair<Array<ArrayElement>, Array<NestedBrace>>

fun empty(): PFNArray = emptyArray<ArrayElement>() to emptyArray()

fun PFNArray.arrayToString() = flat().joinToString("")

fun PFNArray.size() = this.first.size + this.second.size

fun PFNArray.flat() = Array(this.size()) { this[it] }

fun PFNArray.duplicate() = this.flat().toPFN()

fun Array<ArrayElement>.toPFN(): PFNArray {
    val converted = this.foldIndexed(arrayListOf<ArrayElement>() to arrayListOf<NestedBrace>()) { i, acc, e ->
        if (i % 2 == 0) {
            acc.first += e
        } else {
            acc.second += e as NestedBrace
        }
        acc
    }
    return converted.first.toTypedArray() to converted.second.toTypedArray()
}

fun Collection<ArrayElement>.toPFN(): PFNArray {
    val converted = this.foldIndexed(arrayListOf<ArrayElement>() to arrayListOf<NestedBrace>()) { i, acc, e ->
        if (i % 2 == 0) {
            acc.first += e
        } else {
            acc.second += e as NestedBrace
        }
        acc
    }
    return converted.first.toTypedArray() to converted.second.toTypedArray()
}

fun PFNArray.clean(): PFNArray {
    if (this.size() == 0)
        return this
    val firstPass = this.flat()
            .map(ArrayElement::clean)
            .mapIndexed { i, e ->
                if (i % 2 == 1 && e is NestedBrace && e.array.size() == 1 && e.array[0].isZero()) {
                    NestedBrace()
                } else {
                    e
                }
            }
    val first = firstPass[0]
    val secondPass = firstPass.drop(1)
            .windowed(2, 2)
            .toMutableList()
    while (secondPass.lastOrNull()?.get(1)?.isZero(false) == true) {
        secondPass.removeLast()
    }
    val thirdPass = secondPass.reduceOrNull { a, b -> a + b }
            ?.windowed(3, 2, partialWindows = true)
            ?.filterNot { it.size == 3 && it[1].isZero(false) && it[0] < it[2] }
            ?.map { it.take(2) }
            ?.reduceOrNull { a, b -> a + b }
            ?.toMutableList() ?: arrayListOf()
    thirdPass.add(0, first)
    return thirdPass.toPFN()
}

operator fun PFNArray.compareTo(other: PFNArray): Int {
    var a = this
    var b = other
    if (a.size() == 1 && b.size() == 1) {
        if (a[0] is NumberElement && b[0] is NumberElement) {
            return (a[0] as NumberElement).value - (b[0] as NumberElement).value
        } else if (a[0].braceDepth() > 0 || b[0].braceDepth() > 0) {
            val d = a[0].braceDepth() - b[0].braceDepth()
            if (d != 0) {
                return d
            } else {
                a = (a[0] as NestedBrace).peel().array
                b = (b[0] as NestedBrace).peel().array
            }
        } else if (a[0].bracketDepth() > 0 || b[0].bracketDepth() > 0) {
            val d = a[0].bracketDepth() - b[0].bracketDepth()
            if (d != 0) {
                return d
            } else {
                a = (a[0] as NestedArray).peel().array
                b = (b[0] as NestedArray).peel().array
            }
        }
    }
    val k = a.size()
    val l = b.size()
    return if (k == 1 && l > 1 || k > 1 && l == 1) {
        k - l
    } else if (k == 1 && l == 1) {
        a[0].compareTo(b[0])
    } else {
        val ma = (0 until k).filter { i -> (0 until k).all { j -> a[i] >= a[j] } }
        val mb = (0 until l).filter { i -> (0 until l).all { j -> b[i] >= b[j] } }
        val maxma = ma.max()!!
        val maxmb = mb.max()!!
        var c = a[maxma].compareTo(b[maxmb])
        if (c != 0) {
            c
        } else if (ma.size != mb.size) {
            ma.size - mb.size
        } else {
            val aFlat = a.flat()
            val bFlat = b.flat()
            val one = aFlat.copyOfRange(0, maxma).toPFN()
            val two = aFlat.copyOfRange(maxma + 1, aFlat.size).toPFN()
            val three = bFlat.copyOfRange(0, maxmb).toPFN()
            val four = bFlat.copyOfRange(maxma + 1, bFlat.size).toPFN()
            c = two.compareTo(four)
            if (c != 0) {
                c
            } else {
                one.compareTo(three)
            }
        }
    }
}

operator fun PFNArray.get(i: Int) = if (i % 2 == 0) this.first[i / 2] else this.second[i / 2]

operator fun PFNArray.set(i: Int, entry: ArrayElement) = if (i % 2 == 0) {
    this.first[i / 2] = entry
} else {
    this.second[i / 2] = entry as NestedBrace
}

fun MutableList<ReconstructionEntry>.replaceOrAdd(targetIndex: Int, replaceBy: PFNArray, index: Int? = null, brackets: Boolean? = null) {
    for (i in 0 until this.size) {
        val entry = this[i]
        if (entry.globalIndex == targetIndex) {
            this[i] = entry.copy(array = replaceBy, acceptIndex = index ?: entry.acceptIndex, brackets = brackets ?: entry.brackets)
            return
        }
    }
    this.add(ReconstructionEntry(replaceBy, index ?: -1, brackets, targetIndex))
}

fun reconstructFullArray(reconstructionArray: MutableList<ReconstructionEntry>): PFNArray {
    var result = reconstructionArray.removeLast().array
    for ((array, index, brackets) in reconstructionArray.asReversed()) {
        array[index] = if (brackets == null) result[0] else if (brackets) NestedArray(result) else NestedBrace(result)
        result = array
    }
    return result
}

operator fun PFNArray.set(i: Int, n: Int) = if (i % 2 == 0) {
    this.first[i / 2] = NumberElement(n)
} else {
    throw IllegalArgumentException()
}

data class ReconstructionEntry(val array: PFNArray, val acceptIndex: Int, val brackets: Boolean?, val globalIndex: Int) {
    override fun toString(): String {
        return "( " + array.arrayToString() + " ; $acceptIndex ; $brackets ; $globalIndex )"
    }
}

fun String.encloseBraces(layers: Int) = "{".repeat(layers) + this + "}".repeat(layers)

fun parseArray(s: String): PFNArray {
    val entries = arrayListOf<ArrayElement>()
    val separators = arrayListOf<NestedBrace>()
    var i = 0
    var separator = false
    while (i < s.length) {
        val (entry, nextIndex) = parseEntry(s, i, separator)
        if (separator) {
            separators += entry as NestedBrace
        } else {
            entries += entry
        }
        separator = !separator
        i = nextIndex
    }
    return entries.toTypedArray() to separators.toTypedArray()
}

fun parseEntry(s: String, start: Int, separator: Boolean): Pair<ArrayElement, Int> {
    var i = start
    if (i !in s.indices)
        throw IllegalArgumentException("Index isn't in string bounds")
    var end = -1
    var bracketDepth = 0
    if (s[i] == '{' || s[i] == '[') {
        bracketDepth++
    }
    i++
    while (i < s.length) {
        val c = s[i]
        if (bracketDepth == 0) {
            if (c == '{' ||
                separator && (c == '[' || c.isDigit()) ||
                !separator && (c == ',' || c == '{')
            ) {
                end = i
                break
            }
        } else if (c == '[' || c == '{') {
            bracketDepth++
        } else if (c == ']' || c == '}') {
            if (bracketDepth == 0)
                throw IllegalArgumentException("There are unmatching brackets")
            bracketDepth--
        }
        i++
    }
    if (bracketDepth > 0)
        throw IllegalArgumentException("There are unmatching brackets")
    if (end == -1)
        end = s.length
    val entryString = s.substring(start, end)
    return when {
        entryString.toUIntOrNull() != null -> NumberElement(entryString.toInt()) to end
        entryString.startsWith("{") -> NestedBrace(parseArray(entryString.drop(1).dropLast(1))) to end
        entryString.startsWith("[") -> NestedArray(parseArray(entryString.drop(1).dropLast(1))) to end
        entryString == "," -> NestedBrace() to end
        else -> throw IllegalArgumentException("Couldn't parse entry : $entryString")
    }
}

fun ArrayElement.wrap() = when (this) {
    is NumberElement -> listOf(this).toPFN()
    is NestedArray -> this.array
    is NestedBrace -> this.array
}

sealed class ArrayElement(val originalString: String) {
    fun isZero(strict: Boolean = true): Boolean = this is NumberElement && this.value == 0 ||
            !strict && this is NestedBrace && this.array.size() == 1 && this.array[0].isZero(false)

    fun braceDepth(): Int = when {
        this is NestedBrace && this.array.size() == 1 -> this[0].braceDepth() + 1
        this is NestedBrace -> 1
        else -> 0
    }

    fun bracketDepth(): Int = when {
        this is NestedArray && this.array.size() == 1 -> this[0].bracketDepth() + 1
        this is NestedArray -> 1
        else -> 0
    }

    fun firstNumber(): NumberElement = when (this) {
        is NumberElement -> this
        is NestedArray -> array[0].firstNumber()
        is NestedBrace -> array[0].firstNumber()
    }

    abstract fun clean(): ArrayElement

    abstract operator fun compareTo(other: ArrayElement): Int
}

data class NumberElement(val value: Int) : ArrayElement(value.toString()) {
    override fun toString(): String {
        return value.toString()
    }

    override fun equals(other: Any?): Boolean {
        if (other === null || other !is NumberElement)
            return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value
    }

    override fun clean() = this

    override fun compareTo(other: ArrayElement) = if (other is NumberElement) value - other.value else wrap().compareTo(other.wrap())
}

data class NestedArray(val array: PFNArray): ArrayElement(array.arrayToString()) {

    override fun toString(): String {
        return "[" + array.arrayToString() + "]"
    }

    operator fun get(i: Int) = array[i]
    operator fun set(i: Int, value: ArrayElement) {
        array[i] = value
    }

    override fun equals(other: Any?): Boolean {
        if (other === null || other !is NestedArray)
            return false
        return originalString == other.originalString || array.flat().contentEquals(other.array.flat())
    }

    override fun hashCode(): Int {
        return array.hashCode()
    }

    fun peel(): NestedArray = if (array.size() == 1 && array[0] is NestedArray) {
        (array[0] as NestedArray).peel()
    } else {
        this
    }

    override fun clean() = NestedArray(array.clean())

    override fun compareTo(other: ArrayElement) = array.compareTo(other.wrap())
}
data class NestedBrace(val array: PFNArray): ArrayElement(array.arrayToString()) {
    private var comma = false

    constructor(): this(arrayOf<ArrayElement>(NumberElement(0)) to emptyArray()) {
        comma = true
    }

    operator fun get(i: Int) = array[i]

    operator fun set(i: Int, value: ArrayElement) {
        array[i] = value
    }

    override fun toString(): String {
        return if (comma) "," else "{" + array.arrayToString() + "}"
    }

    fun peel(): NestedBrace = if (array.size() == 1 && array[0] is NestedBrace) {
        (array[0] as NestedBrace).peel()
    } else {
        this
    }

    override fun equals(other: Any?): Boolean {
        if (other === null || other !is NestedBrace)
            return false
        return originalString == other.originalString || array.flat().contentEquals(other.array.flat())
    }

    override fun hashCode(): Int {
        return array.hashCode()
    }

    override fun clean() = NestedBrace(array.clean())

    override fun compareTo(other: ArrayElement) = array.compareTo(other.wrap())
}