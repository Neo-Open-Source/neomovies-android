const modules = import.meta.glob('./content/posts/**/*.mdx', { eager: true })

function compareDatesDesc(a, b) {
  return new Date(b.date).getTime() - new Date(a.date).getTime()
}

const groupedPosts = Object.entries(modules).reduce((acc, [modulePath, mod]) => {
  const metadata = mod.metadata
  if (!metadata) return acc

  const generatedCover = `/covers/${metadata.lang}/${metadata.slug}.png`
  const existing = acc.get(metadata.slug) ?? {
    slug: metadata.slug,
    date: metadata.date,
    cover: generatedCover,
    translations: {}
  }

  existing.date = metadata.date
  existing.cover = generatedCover
  existing.translations[metadata.lang] = {
    metadata: {
      ...metadata,
      cover: generatedCover
    },
    Content: mod.default
  }

  acc.set(metadata.slug, existing)
  return acc
}, new Map())

export const posts = Array.from(groupedPosts.values()).sort((a, b) => compareDatesDesc(a, b))

export function getLatestPosts(limit) {
  return posts.slice(0, limit)
}

export function getPostBySlug(slug) {
  return posts.find((post) => post.slug === slug)
}
