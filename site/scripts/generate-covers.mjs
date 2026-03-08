import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import https from 'node:https'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)
const rootDir = path.resolve(__dirname, '..')
const postsDir = path.join(rootDir, 'src', 'content', 'posts')
const outputDir = path.join(rootDir, 'public', 'covers')
const releasesApi = 'https://api.github.com/repos/Neo-Open-Source/neomovies-android/releases?per_page=20'
const caFile = '/private/etc/ssl/cert.pem'

const palettes = [
  ['#34d399', '#2563eb'],
  ['#22d3ee', '#4f46e5'],
  ['#a3e635', '#0ea5e9'],
  ['#14b8a6', '#4338ca'],
  ['#38bdf8', '#6366f1'],
  ['#2dd4bf', '#3b82f6']
]

function walk(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const resolved = path.join(dir, entry.name)
    return entry.isDirectory() ? walk(resolved) : resolved
  })
}

function extractMetadata(source) {
  const slug = source.match(/slug:\s*'([^']+)'/)?.[1]
  const lang = source.match(/lang:\s*'([^']+)'/)?.[1]
  const date = source.match(/date:\s*'([^']+)'/)?.[1]
  const title = source.match(/title:\s*'([^']+)'/)?.[1]
  const excerpt = source.match(/excerpt:\s*'([^']+)'/)?.[1]

  if (!slug || !lang || !date || !title || !excerpt) {
    throw new Error('Post metadata is incomplete')
  }

  return { slug, lang, date, title, excerpt }
}

function wrapText(value) {
  return value.replace(/([:])/g, '$1').trim()
}

function ensureDir(dir) {
  fs.mkdirSync(dir, { recursive: true })
}

function generateCover(metadata) {
  const langDir = path.join(outputDir, metadata.lang)
  ensureDir(langDir)

  const filePath = path.join(langDir, `${metadata.slug}.png`)
  const paletteIndex = Array.from(metadata.slug).reduce((sum, char) => sum + char.charCodeAt(0), 0) % palettes.length
  const [startColor, endColor] = palettes[paletteIndex]

  const title = wrapText(metadata.title)
  execFileSync('convert', [
    '-size', '1200x630', `gradient:${startColor}-${endColor}`,
    '(',
    '-size', '1200x630',
    'xc:none',
    '-fill', 'rgba(255,255,255,0.08)',
    '-draw', 'circle 1080,100 1080,340',
    '-fill', 'rgba(255,255,255,0.06)',
    '-draw', 'circle 940,520 940,740',
    ')',
    '-gravity', 'center',
    '-composite',
    '(',
    '-background', 'none',
    '-fill', '#ffffff',
    '-font', 'AvantGarde-Demi',
    '-gravity', 'northwest',
    '-pointsize', metadata.title.length > 34 ? '58' : '68',
    '-size', '930x220',
    `caption:${title}`,
    ')',
    '-gravity', 'northwest',
    '-geometry', '+68+52',
    '-composite',
    filePath
  ], { stdio: 'inherit' })
}

function pickRelease(releases) {
  const published = releases.filter((release) => !release.draft)
  return published.find((release) => !release.prerelease) ?? published.find((release) => release.prerelease) ?? null
}

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    const request = https.get(url, {
      headers: { 'User-Agent': 'neomovies-site' },
      ca: fs.readFileSync(caFile)
    }, (response) => {
      let raw = ''
      response.on('data', (chunk) => {
        raw += chunk
      })
      response.on('end', () => {
        if (response.statusCode && response.statusCode >= 400) {
          reject(new Error(`GitHub API error: ${response.statusCode}`))
          return
        }

        try {
          resolve(JSON.parse(raw))
        } catch (error) {
          reject(error)
        }
      })
    })

    request.on('error', reject)
  })
}

function generateReleaseCover(target, version) {
  const releaseDir = path.join(outputDir, 'releases')
  ensureDir(releaseDir)

  const filePath = path.join(releaseDir, `${target}-latest.png`)
  const title = target === 'tv' ? `NeoMovies Android TV\n${version}` : `NeoMovies Android\n${version}`
  const [startColor, endColor] = target === 'tv' ? ['#14b8a6', '#2563eb'] : ['#38bdf8', '#4f46e5']

  execFileSync('convert', [
    '-size', '1200x630', `gradient:${startColor}-${endColor}`,
    '(',
    '-size', '1200x630',
    'xc:none',
    '-fill', 'rgba(255,255,255,0.08)',
    '-draw', 'circle 1030,110 1030,330',
    '-fill', 'rgba(255,255,255,0.06)',
    '-draw', 'circle 910,510 910,720',
    ')',
    '-gravity', 'center',
    '-composite',
    '(',
    '-background', 'none',
    '-fill', '#ffffff',
    '-font', 'AvantGarde-Demi',
    '-gravity', 'northwest',
    '-pointsize', '66',
    '-size', '930x240',
    `caption:${title}`,
    ')',
    '-gravity', 'northwest',
    '-geometry', '+68+54',
    '-composite',
    filePath
  ], { stdio: 'inherit' })
}

async function generateReleaseCovers() {
  const releases = await fetchJson(releasesApi)
  const release = pickRelease(releases)
  if (!release) return

  generateReleaseCover('mobile', release.tag_name)
  generateReleaseCover('tv', release.tag_name)
}

async function main() {
  ensureDir(outputDir)

  const postFiles = walk(postsDir).filter((file) => file.endsWith('.mdx'))

  for (const filePath of postFiles) {
    const source = fs.readFileSync(filePath, 'utf8')
    const metadata = extractMetadata(source)
    generateCover(metadata)
  }

  await generateReleaseCovers()
}

await main()
