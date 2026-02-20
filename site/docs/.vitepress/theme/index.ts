import DefaultTheme from 'vitepress/theme'
import { defineComponent, h, onMounted } from 'vue'
import './custom.css'

const TopRightsBanner = defineComponent({
  name: 'TopRightsBanner',
  setup() {
    onMounted(() => {
      const key = '__nmRightsBannerConsoleShown'
      const win = window as unknown as Record<string, unknown>
      if (!win[key]) {
        console.warn(
          '[NeoMovies] LGBTQIA+ Rights: Trans Rights are Human Rights. We support trans people, femboys, and all LGBTQIA+ people.'
        )
        win[key] = true
      }
    })

    return () =>
      h(
        'div',
        {
          class: 'nm-top-rights-banner',
          role: 'note',
          'aria-label': 'LGBTQIA+ rights banner'
        },
        [
          h('img', {
            src: '/pride_flag.avif',
            alt: 'Pride flag',
            class: 'nm-top-rights-flag'
          }),
          h(
            'span',
            { class: 'nm-top-rights-text' },
            'Trans Rights are Human Rights! We support trans people, femboys and all LGBTQIA+ people.'
          )
        ]
      )
  }
})

export default {
  ...DefaultTheme,
  Layout: () =>
    h(DefaultTheme.Layout, null, {
      'layout-top': () => h(TopRightsBanner)
    })
}
