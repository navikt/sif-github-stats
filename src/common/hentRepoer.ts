import * as fs from 'fs'

export function hentRepoer(): string[] {
    const repos: string[] = []
    const file = fs.readFileSync('./repos.txt', 'utf8') as string
    file.split('\n').forEach((line) => {
        if (line) repos.push(line)
    })

    return repos
}