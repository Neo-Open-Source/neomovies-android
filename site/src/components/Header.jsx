import Button from '@jetbrains/ring-ui-built/components/button/button'
import { Link, useLocation } from 'react-router-dom'
import { withLang, withLangHash } from '../lib/i18n'

export default function Header({ lang, t }) {
  const location = useLocation()
  const homeHash = (hash) => (location.pathname === '/' ? hash : withLangHash('/', lang, hash))

  return (
    <header className="nm-header">
      <div className="nm-shell nm-header-top">
        <Link className="nm-logo" to={withLang('/', lang)}>
          <img
            className="nm-logo-image"
            src="https://raw.githubusercontent.com/Neo-Open-Source/neomovies-android/main/.github/assets/logo.png"
            alt="NeoMovies logo"
          />
          <span>NeoMovies</span>
        </Link>
        <nav className="nm-main-nav">
          {location.pathname === '/' ? (
            <a href="#features">{t.nav.features}</a>
          ) : (
            <Link to={homeHash('#features')}>{t.nav.features}</Link>
          )}
          {location.pathname === '/' ? (
            <a href="#gallery">{t.nav.screens}</a>
          ) : (
            <Link to={homeHash('#gallery')}>{t.nav.screens}</Link>
          )}
          <Link to={withLang('/news', lang)}>{t.nav.news}</Link>
          <Link to={withLang('/download', lang)}>{t.nav.download}</Link>
          <a href="https://github.com/Neo-Open-Source">{t.nav.github}</a>
        </nav>
        <div className="nm-header-actions">
          <Link className="nm-link-btn" to={withLang('/download', lang)}>
            <Button className="nm-pill-button">{t.nav.download}</Button>
          </Link>
        </div>
      </div>
    </header>
  )
}
