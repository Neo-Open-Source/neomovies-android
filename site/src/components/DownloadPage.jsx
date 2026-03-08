import { useEffect, useMemo, useState } from 'react'
import Button from '@jetbrains/ring-ui-built/components/button/button'
import { Link } from 'react-router-dom'
import { HiChevronDown } from 'react-icons/hi2'
import Footer from './Footer'
import Header from './Header'
import { formatDate, withLang } from '../lib/i18n'

const RELEASES_API = 'https://api.github.com/repos/Neo-Open-Source/neomovies-android/releases?per_page=20'
const RELEASE_CACHE_KEY = 'neomovies_release_cache_v1'
const RELEASE_CACHE_TTL = 1000 * 60 * 30

function pickRelease(releases) {
  const published = releases.filter((release) => !release.draft)
  return published.find((release) => !release.prerelease) ?? published.find((release) => release.prerelease) ?? null
}

function normalizeArchitecture(assetName) {
  if (assetName.includes('universal')) return 'universal'
  if (assetName.includes('arm64-v8a')) return 'arm64-v8a'
  if (assetName.includes('armeabi-v7a')) return 'armeabi-v7a'
  return null
}

function groupAssets(assets) {
  const groups = {
    mobile: {},
    tv: {}
  }

  for (const asset of assets) {
    const architecture = normalizeArchitecture(asset.name)
    if (!architecture) continue

    const target = asset.name.startsWith('neomovies-tv-') ? 'tv' : 'mobile'
    groups[target][architecture] = asset
  }

  return groups
}

function formatSize(bytes) {
  if (!bytes) return ''
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}

export default function DownloadPage({ lang, setLang, t }) {
  const [state, setState] = useState({ loading: true, error: '', release: null })
  const [target, setTarget] = useState('mobile')
  const [isMenuOpen, setIsMenuOpen] = useState(false)

  useEffect(() => {
    let cancelled = false

    function readCache() {
      try {
        const raw = localStorage.getItem(RELEASE_CACHE_KEY)
        if (!raw) return null
        const parsed = JSON.parse(raw)
        if (!parsed?.release || !parsed?.fetchedAt) return null
        return parsed
      } catch {
        return null
      }
    }

    function writeCache(release, etag = '') {
      try {
        localStorage.setItem(RELEASE_CACHE_KEY, JSON.stringify({
          fetchedAt: Date.now(),
          etag,
          release
        }))
      } catch {
        // ignore storage write failures
      }
    }

    async function loadRelease() {
      const cachedEntry = readCache()
      const cachedRelease = cachedEntry?.release ?? null
      const isFreshCache = cachedEntry && Date.now() - cachedEntry.fetchedAt <= RELEASE_CACHE_TTL

      if (cachedRelease && !cancelled) {
        setState({ loading: !isFreshCache, error: '', release: cachedRelease })
      }

      if (isFreshCache) {
        return
      }

      try {
        if (!cachedRelease) {
          setState({ loading: true, error: '', release: null })
        }
        const response = await fetch(RELEASES_API, {
          headers: cachedEntry?.etag ? { 'If-None-Match': cachedEntry.etag } : undefined
        })

        if (response.status === 304 && cachedRelease) {
          writeCache(cachedRelease, cachedEntry?.etag ?? '')
          if (!cancelled) {
            setState({ loading: false, error: '', release: cachedRelease })
          }
          return
        }

        if (!response.ok) {
          throw new Error(`GitHub API error: ${response.status}`)
        }

        const releases = await response.json()
        const release = pickRelease(releases)

        if (!release) {
          throw new Error('No releases found')
        }

        writeCache(release, response.headers.get('etag') ?? '')

        if (!cancelled) {
          setState({ loading: false, error: '', release })
        }
      } catch (error) {
        if (!cancelled) {
          setState({
            loading: false,
            error: error instanceof Error ? error.message : 'Unknown error',
            release: cachedRelease
          })
        }
      }
    }

    loadRelease()
    return () => {
      cancelled = true
    }
  }, [])

  const groupedAssets = useMemo(
    () => (state.release ? groupAssets(state.release.assets ?? []) : { mobile: {}, tv: {} }),
    [state.release]
  )

  const architectureOrder = ['universal', 'arm64-v8a', 'armeabi-v7a']
  const selectedAssets = groupedAssets[target]
  const availableAssets = architectureOrder.map((key) => selectedAssets[key]).filter(Boolean)
  const previewImage = state.release
    ? `/covers/releases/${target}-latest.png?v=${encodeURIComponent(state.release.tag_name)}`
    : `/covers/releases/${target}-latest.png`

  const getArchitectureLabel = (assetName) => {
    const key = normalizeArchitecture(assetName)
    if (key === 'universal') return t.downloadPage.archUniversal
    if (key === 'arm64-v8a') return t.downloadPage.archArm64
    if (key === 'armeabi-v7a') return t.downloadPage.archArmv7
    return assetName
  }

  return (
    <div className="nm-terms-page nm-download-page">
      <Header lang={lang} t={t} />
      <main className="nm-terms-shell">
        <div className="nm-download-page-topline">
          <Link to={withLang('/', lang)}>{lang === 'ru' ? 'На главную' : 'Back to home'}</Link>
        </div>

        <section className="nm-download-page-hero">
          <div>
            <div className="nm-download-tabs" role="tablist" aria-label={t.downloadPage.targetLabel}>
              <button
                type="button"
                className={`nm-download-tab${target === 'mobile' ? ' is-active' : ''}`}
                onClick={() => {
                  setTarget('mobile')
                  setIsMenuOpen(false)
                }}
              >
                {t.downloadPage.mobileTab}
              </button>
              <button
                type="button"
                className={`nm-download-tab${target === 'tv' ? ' is-active' : ''}`}
                onClick={() => {
                  setTarget('tv')
                  setIsMenuOpen(false)
                }}
              >
                {t.downloadPage.tvTab}
              </button>
            </div>
            <h1>{t.downloadPage.title}</h1>
            <p>{target === 'mobile' ? t.downloadPage.mobileText : t.downloadPage.tvText}</p>
            <section className="nm-download-selector">
              {state.error ? (
                <p className="nm-download-selector-note">{t.downloadPage.error}</p>
              ) : null}
              <div className="nm-download-menu-wrap">
                <button
                  type="button"
                  className="nm-download-action"
                  onClick={() => setIsMenuOpen((value) => !value)}
                  disabled={!availableAssets.length}
                >
                  <span>{t.downloadPage.downloadButton}</span>
                  <HiChevronDown className={isMenuOpen ? 'is-open' : ''} />
                </button>

                {isMenuOpen && availableAssets.length ? (
                  <div className="nm-download-menu">
                    {availableAssets.map((asset) => (
                      <a
                        key={asset.id ?? asset.name}
                        className="nm-download-menu-item"
                        href={asset.browser_download_url}
                        onClick={() => setIsMenuOpen(false)}
                      >
                        <span>{getArchitectureLabel(asset.name)}</span>
                        <small>{formatSize(asset.size)}</small>
                      </a>
                    ))}
                  </div>
                ) : null}
              </div>

              <p className="nm-download-selector-note">
                {t.downloadPage.selectorNote}
              </p>
            </section>
          </div>
          <aside className="nm-download-preview">
            <div className="nm-download-preview-image">
              <img src={previewImage} alt={target === 'tv' ? 'NeoMovies TV release cover' : 'NeoMovies mobile release cover'} />
            </div>
            <div className="nm-download-preview-meta">
              {state.loading ? <span>{t.downloadPage.loading}</span> : null}
              {state.release ? (
                <>
                  <div className="nm-download-preview-facts">
                    <span>{t.downloadPage.versionLabel}</span>
                    <strong>{state.release.tag_name}</strong>
                    <span>{t.downloadPage.dateLabel}</span>
                    <strong>{formatDate(state.release.published_at, lang)}</strong>
                    <span>{t.downloadPage.channelLabel}</span>
                    <strong>{state.release.prerelease ? t.downloadPage.prerelease : t.downloadPage.stable}</strong>
                  </div>
                  <div className="nm-download-preview-links">
                    <a href={state.release.html_url}>{t.downloadPage.releaseNotes}</a>
                    <a href="https://github.com/Neo-Open-Source/neomovies-android/releases">{t.downloadPage.allReleases}</a>
                  </div>
                </>
              ) : null}
              {state.error ? <span>{t.downloadPage.fallback}</span> : null}
            </div>
          </aside>
        </section>
      </main>
      <Footer lang={lang} t={t} setLang={setLang} />
    </div>
  )
}
