import { useState } from 'react'
import Button from '@jetbrains/ring-ui-built/components/button/button'

export default function IssueSection({ lang, t }) {
  const [issueText, setIssueText] = useState('')

  const openIssue = (event) => {
    event.preventDefault()
    const text = issueText.trim()
    if (!text) return

    const title = encodeURIComponent(lang === 'ru' ? 'Проблема в NeoMovies' : 'NeoMovies issue')
    const body = encodeURIComponent(text)

    window.open(
      `https://github.com/Neo-Open-Source/neomovies-android/issues/new?title=${title}&body=${body}`,
      '_blank',
      'noopener,noreferrer'
    )
  }

  return (
    <section className="nm-issue-block">
      <div>
        <h2>{t.issue.title}</h2>
        <p>{t.issue.text}</p>
      </div>
      <form className="nm-issue-form" onSubmit={openIssue}>
        <textarea
          value={issueText}
          onChange={(event) => setIssueText(event.target.value)}
          placeholder={t.issue.placeholder}
          rows={5}
        />
        <Button
          className="nm-pill-button"
          type="submit"
          disabled={!issueText.trim()}
        >
          {t.issue.submit}
        </Button>
      </form>
    </section>
  )
}
