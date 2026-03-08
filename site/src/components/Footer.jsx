import { useState } from 'react'
import { FaGithub, FaGitlab } from 'react-icons/fa6'
import { HiLanguage } from 'react-icons/hi2'
import { IoEarthSharp } from 'react-icons/io5'
import { RiTelegram2Fill } from 'react-icons/ri'
import { Link } from 'react-router-dom'
import { withLang } from '../lib/i18n'

export default function Footer({ lang, t, setLang }) {
  const [isLangOpen, setIsLangOpen] = useState(false)

  return (
    <footer id="footer" className="nm-footer">
      <div className="nm-shell nm-footer-grid">
        {t.footer.sections.map((section) => (
          <div key={section.title} className="nm-footer-section">
            <h3>{section.title}</h3>
            {section.links.map((item) => {
              const href = item.href.startsWith('/') ? withLang(item.href, lang) : item.href
              return item.href.startsWith('/') ? (
                <Link key={item.label} to={href}>{item.label}</Link>
              ) : (
                <a key={item.label} href={href}>{item.label}</a>
              )
            })}
          </div>
        ))}
      </div>

      <div className="nm-shell nm-footer-meta">
        <div className="nm-community-icons">
          <a href="https://t.me/neomovies_news" className="nm-community-icon" aria-label="Telegram">
            <RiTelegram2Fill />
          </a>
          <a href="https://github.com/Neo-Open-Source" className="nm-community-icon" aria-label="GitHub">
            <FaGithub />
          </a>
          <a href="https://gitlab.com/foxixus/neomovies-web" className="nm-community-icon" aria-label="GitLab">
            <FaGitlab />
          </a>
          <a href="https://www.neomovies.ru" className="nm-community-icon" aria-label="NeoMovies">
            <IoEarthSharp />
          </a>
        </div>

        <div className="nm-footer-tags">
          {t.footer.tags.map((item) => {
            const href = item.href.startsWith('/') ? withLang(item.href, lang) : item.href
            return item.href.startsWith('/') ? (
              <Link key={item.label} to={href}>{item.label}</Link>
            ) : (
              <a key={item.label} href={href}>{item.label}</a>
            )
          })}
        </div>

        <div className="nm-footer-side">
          <div className="nm-footer-lang-wrap">
            <button className="nm-footer-lang" type="button" onClick={() => setIsLangOpen((value) => !value)}>
              <HiLanguage />
            </button>
            {isLangOpen ? (
              <div className="nm-footer-lang-menu">
                <button
                  className={`nm-footer-lang-option${lang === 'en' ? ' is-active' : ''}`}
                  type="button"
                  onClick={() => {
                    setLang('en')
                    setIsLangOpen(false)
                  }}
                >
                  English
                </button>
                <button
                  className={`nm-footer-lang-option${lang === 'ru' ? ' is-active' : ''}`}
                  type="button"
                  onClick={() => {
                    setLang('ru')
                    setIsLangOpen(false)
                  }}
                >
                  Русский
                </button>
              </div>
            ) : null}
          </div>
          <span>{t.footer.copyright}</span>
        </div>
      </div>
    </footer>
  )
}
