import { Link } from 'react-router-dom'
import { getLatestPosts } from '../content'
import { formatDate, withLang } from '../lib/i18n'
import MobileCarousel from './MobileCarousel'

export default function NewsSection({ lang, t }) {
  const latestPosts = getLatestPosts(3)

  return (
    <section id="news" className="nm-news">
      <div className="nm-section-head">
        <h2>{t.news.title}</h2>
        <Link className="nm-more-link" to={withLang('/news', lang)}>{t.news.action}</Link>
      </div>
      <MobileCarousel className="nm-news-carousel" trackClassName="nm-news-grid">
        {latestPosts.map((post) => {
          const entry = post.translations[lang] ?? post.translations.ru ?? post.translations.en
          if (!entry) return null

          return (
            <Link key={post.slug} className="nm-news-card" to={withLang(`/posts/${post.slug}`, lang)}>
              <img src={entry.metadata.cover} alt={entry.metadata.title} />
              <div className="nm-news-card-body">
                <span>{formatDate(entry.metadata.date, lang)}</span>
                <h3>{entry.metadata.title}</h3>
                <p>{entry.metadata.excerpt}</p>
              </div>
            </Link>
          )
        })}
      </MobileCarousel>
    </section>
  )
}
