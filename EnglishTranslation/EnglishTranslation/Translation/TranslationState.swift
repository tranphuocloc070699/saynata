//
//  TranslationState.swift
//  EnglishTranslation
//
//  Created by Trần Phước Lộc on 7/10/26.
//
import Foundation


enum TranslationState {
    ///Nothing has been requested yet
    case idle
    ///The request is in flight
    case loading
    ///The request worked. Holds the explanation and the translation
    case loaded(Explanation)
    ///The request failed. Holds a message the user can read
    case failed(String)

}
