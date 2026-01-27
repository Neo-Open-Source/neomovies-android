import { defineConfig } from 'vitepress'

export default defineConfig({
  lang: 'en-US',
  title: 'NeoMovies',
  description: 'Download NeoMovies for Android',
  themeConfig: {
    nav: [
      { text: 'Download', link: '/download' },
      { text: 'GitHub', link: 'https://github.com/Neo-Open-Source/neomovies-android/releases' }
    ],
    sidebar: [
      { text: 'Download', link: '/download' }
    ],
    socialLinks: [
      { icon: 'github', link: 'https://github.com/Neo-Open-Source/neomovies-android' }
    ]
  }
})
