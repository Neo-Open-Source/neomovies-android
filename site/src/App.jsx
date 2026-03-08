import React, { useEffect, useState } from 'react'
import Button from '@jetbrains/ring-ui-built/components/button/button'
import { Link, Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { getPostBySlug, posts } from './content'
import Footer from './components/Footer'
import DownloadPage from './components/DownloadPage'
import Header from './components/Header'
import IssueSection from './components/IssueSection'
import MobileCarousel from './components/MobileCarousel'
import NewsSection from './components/NewsSection'
import ScrollToTop from './components/ScrollToTop'
import { content, detectInitialLang, formatDate, withLang } from './lib/i18n'

const screenshots = [
  'https://raw.githubusercontent.com/Neo-Open-Source/neomovies-android/main/.github/assets/Screenshot_1.png',
  'https://raw.githubusercontent.com/Neo-Open-Source/neomovies-android/main/.github/assets/Screenshot_2.png',
  'https://raw.githubusercontent.com/Neo-Open-Source/neomovies-android/main/.github/assets/Screenshot_3.png'
]

function LandingPage({ lang, setLang }) {
  const t = content[lang]

  return (
    <div className="nm-site">
      <Header lang={lang} t={t} />

      <main className="nm-shell">
        <section id="hero" className="nm-hero">
          <div className="nm-hero-copy">
            <p className="nm-eyebrow">{t.hero.kicker}</p>
            <h1>{t.hero.title}</h1>
            <p>{t.hero.text}</p>
            <div className="nm-cta-row">
              <Link className="nm-link-btn" to={withLang('/download', lang)}>
                <Button className="nm-pill-button">{t.hero.download}</Button>
              </Link>
              <a className="nm-link-btn" href="https://t.me/neomovies_news">
                <Button className="nm-pill-button">{t.hero.telegram}</Button>
              </a>
            </div>
          </div>
        </section>

        <section id="features" className="nm-feature-strip">
          {t.features.map((item) => (
            <article key={item.title}>
              <h3>{item.title}</h3>
              <p>{item.text}</p>
            </article>
          ))}
        </section>

        <section className="nm-showcase">
          <div className="nm-showcase-copy">
            <h2>{t.about.title}</h2>
            <p>{t.about.text}</p>
            <ul>
              {t.about.points.map((item) => (
                <li key={item}>{item}</li>
              ))}
            </ul>
          </div>
        </section>

        <MobileCarousel className="nm-gallery-carousel" trackClassName="nm-gallery" compact>
          {screenshots.map((src, index) => (
            <article key={`${src}-${index}`} className="nm-shot">
              <img src={src} alt={`NeoMovies screenshot ${index + 1}`} />
            </article>
          ))}
        </MobileCarousel>

        <NewsSection lang={lang} t={t} />

        <section id="download" className="nm-download-block">
          <div>
            <h2>{t.download.title}</h2>
            <p>{t.download.text}</p>
          </div>
          <div className="nm-cta-row">
            <Link className="nm-link-btn" to={withLang('/download', lang)}>
              <Button className="nm-pill-button">{t.download.stable}</Button>
            </Link>
            <a className="nm-link-btn" href="https://github.com/Neo-Open-Source/neomovies-android/releases">
              <Button className="nm-pill-button">{t.download.preview}</Button>
            </a>
          </div>
        </section>

        <IssueSection lang={lang} t={t} />
      </main>

      <Footer lang={lang} t={t} setLang={setLang} />
    </div>
  )
}

function AllNewsPage({ lang, setLang }) {
  const t = content[lang]
  const [visibleCount, setVisibleCount] = useState(10)
  const visiblePosts = posts.slice(0, visibleCount)

  return (
    <div className="nm-terms-page nm-news-page">
      <Header lang={lang} t={t} />
      <main className="nm-terms-shell">
        <div className="nm-news-page-head">
          <span>{t.news.title}</span>
          <Link to={withLang('/', lang)}>{lang === 'ru' ? 'На главную' : 'Back to home'}</Link>
        </div>
        <div className="nm-news-page-intro">
          <h1>{t.news.title}</h1>
        </div>
        <div className="nm-news-archive-grid">
          {visiblePosts.map((post) => {
            const entry = post.translations[lang] ?? post.translations.ru ?? post.translations.en
            if (!entry) return null

            return (
              <Link key={post.slug} className="nm-news-archive-card" to={withLang(`/posts/${post.slug}`, lang)}>
                <img src={entry.metadata.cover} alt={entry.metadata.title} />
                <div className="nm-news-archive-body">
                  <span>{formatDate(entry.metadata.date, lang)}</span>
                  <h3>{entry.metadata.title}</h3>
                  <p>{entry.metadata.excerpt}</p>
                </div>
              </Link>
            )
          })}
        </div>
        {posts.length > visibleCount ? (
          <div className="nm-news-load-more">
            <Button className="nm-pill-button" onClick={() => setVisibleCount((count) => count + 10)}>
              {t.news.loadMore}
            </Button>
          </div>
        ) : null}
      </main>
      <Footer lang={lang} t={t} setLang={setLang} />
    </div>
  )
}

function TermsPage({ lang, setLang }) {
  const t = content[lang]

  return (
    <div className="nm-terms-page">
      <Header lang={lang} t={t} />
      <main className="nm-terms-shell">
        <div className="nm-terms-topline">
          <span>{t.terms.label}</span>
          <Link to={withLang('/', lang)}>{lang === 'ru' ? 'На главную' : 'Back to home'}</Link>
        </div>
        <h1>{t.terms.title}</h1>
        <p className="nm-terms-version">{t.terms.version}</p>
        <div className="nm-terms-body">
          {t.terms.sections.map((section) => (
            <section key={section.title} className="nm-terms-section">
              <h2>{section.title}</h2>
              {section.text.split('\n\n').map((paragraph) => (
                <p key={`${section.title}-${paragraph}`}>{paragraph}</p>
              ))}
              {section.list ? (
                <ul>
                  {section.list.map((item) => (
                    <li key={item}>{item}</li>
                  ))}
                </ul>
              ) : null}
              {section.afterList ? <p>{section.afterList}</p> : null}
            </section>
          ))}
        </div>
      </main>
      <Footer lang={lang} t={t} setLang={setLang} />
    </div>
  )
}

function PostPage({ lang, setLang }) {
  const { slug } = useParams()
  const post = getPostBySlug(slug)

  if (!post) {
    return <Navigate to={withLang('/', lang)} replace />
  }

  const t = content[lang]
  const entry = post.translations[lang] ?? post.translations.ru ?? post.translations.en
  const Content = entry.Content

  return (
    <div className="nm-terms-page nm-post-page">
      <Header lang={lang} t={t} />
      <main className="nm-terms-shell">
        <div className="nm-terms-topline">
          <span>{entry.metadata.category}</span>
          <Link className="nm-post-back" to={withLang('/news', lang)}>
            {lang === 'ru' ? 'Назад к статьям' : 'Back to articles'}
          </Link>
        </div>
        <article className="nm-post-article">
          <h1>{entry.metadata.title}</h1>
          <p className="nm-terms-version">{formatDate(entry.metadata.date, lang)}</p>
          <div className="nm-post-cover">
            <img src={entry.metadata.cover} alt={entry.metadata.title} />
          </div>
          <div className="nm-post-body">
            <Content />
          </div>
        </article>
      </main>
      <Footer lang={lang} t={t} setLang={setLang} />
    </div>
  )
}

function RoutedApp() {
  const location = useLocation()
  const navigate = useNavigate()
  const [lang, setLang] = useState(() => {
    const params = new URLSearchParams(location.search)
    const urlLang = params.get('lang')
    return urlLang === 'ru' || urlLang === 'en' ? urlLang : detectInitialLang()
  })

  const handleSetLang = (nextLang) => {
    if (nextLang !== 'ru' && nextLang !== 'en') return
    if (nextLang === lang) return

    const params = new URLSearchParams(location.search)
    params.set('lang', nextLang)
    setLang(nextLang)
    navigate(`${location.pathname}?${params.toString()}${location.hash}`, { replace: true })
  }

  useEffect(() => {
    const params = new URLSearchParams(location.search)
    const urlLang = params.get('lang')

    if (urlLang === 'ru' || urlLang === 'en') {
      if (urlLang !== lang) {
        setLang(urlLang)
      }
    } else {
      params.set('lang', lang)
      navigate(`${location.pathname}?${params.toString()}${location.hash}`, { replace: true })
    }
  }, [lang, location.pathname, location.search, location.hash, navigate])

  return (
    <Routes>
      <Route path="/" element={<LandingPage lang={lang} setLang={handleSetLang} />} />
      <Route path="/download" element={<DownloadPage lang={lang} setLang={handleSetLang} t={content[lang]} />} />
      <Route path="/news" element={<AllNewsPage lang={lang} setLang={handleSetLang} />} />
      <Route path="/terms" element={<TermsPage lang={lang} setLang={handleSetLang} />} />
      <Route path="/posts/:slug" element={<PostPage lang={lang} setLang={handleSetLang} />} />
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default function App() {
  return (
    <>
      <ScrollToTop />
      <RoutedApp />
    </>
  )
}
