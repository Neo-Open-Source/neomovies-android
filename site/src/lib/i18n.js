import en from '../i18n/en.json'
import ru from '../i18n/ru.json'

export const content = { en, ru }

export function detectInitialLang() {
  if (typeof navigator === 'undefined') return 'en'
  return navigator.language.toLowerCase().startsWith('ru') ? 'ru' : 'en'
}

export function formatDate(value, lang) {
  return new Intl.DateTimeFormat(lang === 'ru' ? 'ru-RU' : 'en-US', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  }).format(new Date(value))
}

export function withLang(path, lang) {
  return `${path}?lang=${lang}`
}

export function withLangHash(path, lang, hash) {
  return `${withLang(path, lang)}${hash}`
}
