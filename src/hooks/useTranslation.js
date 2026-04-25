import { useState, useEffect } from 'react'
import en_main from '../i18n/en/main'
import en_hero from '../i18n/en/hero'
import en_config_base from '../i18n/en/config_base'
import en_config_cat from '../i18n/en/config_categories'
import en_content from '../i18n/en/content'
import fr_main from '../i18n/fr/main'
import fr_hero from '../i18n/fr/hero'
import fr_config_base from '../i18n/fr/config_base'
import fr_config_cat from '../i18n/fr/config_categories'
import fr_content from '../i18n/fr/content'

const en = { ...en_main, ...en_hero, ...en_config_base, config: { ...en_config_base.config, ...en_config_cat.config }, ...en_content }
const fr = { ...fr_main, ...fr_hero, ...fr_config_base, config: { ...fr_config_base.config, ...fr_config_cat.config }, ...fr_content }
const languages = { en, fr }

export function useTranslation() {
  const [lang, setLang] = useState('en')
  useEffect(() => {
    const browserLang = navigator.language.split('-')[0]
    if (languages[browserLang]) setLang(browserLang)
  }, [])

  const t = (path) => {
    const keys = path.split('.')
    let current = languages[lang]
    for (const key of keys) {
      if (!current[key]) return path
      current = current[key]
    }
    return current
  }
  return { t, lang }
}
