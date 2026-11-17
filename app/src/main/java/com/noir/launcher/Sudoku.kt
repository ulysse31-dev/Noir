package com.noir.launcher

import kotlin.random.Random

/** Moteur de sudoku : génération de grilles à solution unique, détection des doublons. */
object Sudoku {
    /** Nombre d'indices de départ selon la difficulté (0 facile, 1 moyen, 2 difficile). */
    val CLUES = intArrayOf(40, 32, 26)

    /** Renvoie (grille à remplir, solution). Les cases vides valent 0. */
    fun generate(clues: Int, rnd: Random = Random.Default): Pair<IntArray, IntArray> {
        val solution = IntArray(81)
        Solver(solution, rnd).solve(limit = 1, keep = true)

        val puzzle = solution.copyOf()
        var left = 81
        for (i in (0..80).shuffled(rnd)) {
            if (left <= clues) break
            val keep = puzzle[i]
            puzzle[i] = 0
            val probe = Solver(puzzle.copyOf(), null)
            probe.solve(limit = 2, keep = false)
            if (probe.count != 1) puzzle[i] = keep else left--
        }
        return puzzle to solution
    }

    /** Cases dont le chiffre apparaît déjà dans la même ligne, colonne ou région. */
    fun conflicts(g: IntArray): BooleanArray {
        val bad = BooleanArray(81)
        for (i in 0..80) {
            val d = g[i]
            if (d == 0) continue
            for (j in 0..80) {
                if (j == i || g[j] != d) continue
                if (i / 9 == j / 9 || i % 9 == j % 9 || box(i) == box(j)) {
                    bad[i] = true
                    break
                }
            }
        }
        return bad
    }

    fun box(i: Int): Int = (i / 27) * 3 + (i % 9) / 3

    fun encode(g: IntArray): String = g.joinToString("")

    fun decode(s: String?): IntArray? =
        if (s != null && s.length == 81 && s.all { it in '0'..'9' }) IntArray(81) { s[it] - '0' } else null

    private class Solver(val g: IntArray, val rnd: Random?) {
        private val rows = IntArray(9)
        private val cols = IntArray(9)
        private val boxes = IntArray(9)
        var count = 0

        init {
            for (i in 0..80) if (g[i] != 0) mark(i, g[i], true)
        }

        private fun mark(i: Int, d: Int, on: Boolean) {
            val bit = 1 shl d
            val r = i / 9
            val c = i % 9
            val b = box(i)
            if (on) {
                rows[r] = rows[r] or bit; cols[c] = cols[c] or bit; boxes[b] = boxes[b] or bit
            } else {
                rows[r] = rows[r] and bit.inv(); cols[c] = cols[c] and bit.inv(); boxes[b] = boxes[b] and bit.inv()
            }
        }

        /** Renvoie true quand il faut s'arrêter (assez de solutions trouvées). */
        fun solve(limit: Int, keep: Boolean): Boolean {
            var best = -1
            var bestMask = 0
            var bestN = 10
            for (i in 0..80) {
                if (g[i] != 0) continue
                val avail = (rows[i / 9] or cols[i % 9] or boxes[box(i)]).inv() and 0x3FE
                val n = Integer.bitCount(avail)
                if (n < bestN) {
                    best = i; bestMask = avail; bestN = n
                    if (n <= 1) break
                }
            }
            if (best == -1) {
                count++
                return count >= limit
            }
            if (bestN == 0) return false
            var digits = (1..9).filter { bestMask and (1 shl it) != 0 }
            if (rnd != null) digits = digits.shuffled(rnd)
            for (d in digits) {
                g[best] = d
                mark(best, d, true)
                val stop = solve(limit, keep)
                if (stop && keep) return true
                mark(best, d, false)
                g[best] = 0
                if (stop) return true
            }
            return false
        }
    }
}
