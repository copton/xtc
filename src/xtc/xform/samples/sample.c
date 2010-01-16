/*
 * xtc - The eXTensible Compiler
 * Copyright (C) 2005-2006 New York University
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301,
 * USA.
 */
int main() {
  int i, j, k, m;
  int q;

  while (m <100) {
    m++;
  }

  do {
    q++;
  } while (q<100);

  for(i = 0; i<10; i++) {
    i++;
  }

  for(i = 0; i<10; i++) {
    i++;
    int j;
    for (j = 0; j<10; j++) {
      j++;
      static double r, n, o, p;
    }
  }

  return i;
}
