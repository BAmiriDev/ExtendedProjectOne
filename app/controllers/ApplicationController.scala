package controllers
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import services.LibraryService
import controllers.models.{APIError, DataModel}
import views.html.myGoogleBook

import controllers.models.{APIError, DataModel}
import play.api.libs.json.{JsError, JsSuccess, JsValue, Json}
import play.api.mvc._
import repositories.DataRepository
import services.{LibraryService, RepositoryService}
import play.filters.csrf.CSRF

import javax.inject._
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class ApplicationController @Inject()(
                                       val controllerComponents: ControllerComponents,
                                       dataRepository: DataRepository,
                                       libraryService: LibraryService,

                                     )(implicit ec: ExecutionContext) extends BaseController with play.api.i18n.I18nSupport{

  def index(): Action[AnyContent] = Action.async { implicit request =>
    dataRepository.index().map {
      case Right(items) => Ok(Json.toJson(items))
      case Left(error) => Status(error.httpResponseStatus)(Json.toJson(error.reason))
    }
  }

  def create(): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[DataModel] match {
      case JsSuccess(dataModel, _) =>
        dataRepository.create(dataModel).map {
          case Right(book) => Created(Json.toJson(book))
          case Left(error) => Status(error.httpResponseStatus)(Json.toJson(error.reason))
        }
      case JsError(errors) => Future.successful(BadRequest(Json.obj("errors" -> errors.toString)))
    }
  }

  def read(id: String): Action[AnyContent] = Action.async { implicit request =>
    dataRepository.read(id).map {
      case Right(dataModel) => Ok(Json.toJson(dataModel))
      case Left(error) => Status(if (error.upstreamStatus == 404) NOT_FOUND else INTERNAL_SERVER_ERROR)(Json.toJson(error.reason))
    }
  }

  def update(id: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    request.body.validate[DataModel] match {
      case JsSuccess(dataModel, _) =>
        dataRepository.update(id, dataModel).map {
          case Right(count) if count > 0 => Accepted
          case Left(error) => Status(error.httpResponseStatus)(Json.toJson(error.reason))
          case _ => NotFound
        }
      case JsError(errors) => Future.successful(BadRequest(Json.obj("errors" -> errors.toString)))
    }
  }

  def delete(id: String): Action[AnyContent] = Action.async { implicit request =>
    if (id.trim.isEmpty) {
      Future.successful(BadRequest(Json.obj("error" -> "Invalid ID")))
    } else {
      dataRepository.delete(id).map {
        case Right(count) if count > 0 => Accepted
        case Left(error) => Status(error.httpResponseStatus)(Json.toJson(error.reason))
        case _ => NotFound
      }
    }
  }

  def getGoogleBook(search: String, term: String): Action[AnyContent] = Action.async { implicit request =>
    libraryService.getGoogleBook(search = search, term = term).value.map {
      case Right(book) => Ok(Json.toJson(book))
      case Left(error) => InternalServerError(error.reason)
    }
  }



  def findByName(name: String): Action[AnyContent] = Action.async { implicit request =>
    dataRepository.findByName(name).map {
      case Right(items) => Ok(Json.toJson(items))
      case Left(error) => Status(if (error.upstreamStatus == 404) NOT_FOUND else INTERNAL_SERVER_ERROR)(Json.toJson(error.reason))
    }
  }


  def updateField(id: String, fieldName: String): Action[JsValue] = Action.async(parse.json) { implicit request =>
    (request.body \ "value").validate[JsValue] match {
      case JsSuccess(newValue, _) =>
        dataRepository.updateField(id, fieldName, newValue).map {
          case Right(_) => Accepted
          case Left(error) => Status(error.httpResponseStatus)(Json.toJson(error.reason))
        }
      case JsError(errors) => Future.successful(BadRequest(Json.obj("errors" -> errors.toString)))
    }
  }

  def getByISBN(isbn: String): Action[AnyContent] = Action.async { implicit request =>
    libraryService.getByISBN(isbn, dataRepository).value.map {
      case Right(dataModel) => Ok(myGoogleBook(dataModel))
      case Left(error) => InternalServerError(error.reason)
    }
  }



  def example(id: String): Action[AnyContent] = Action.async { implicit request =>
    dataRepository.read(id).map {
      case Right(dataModel) => Ok(views.html.example(dataModel))
      case Left(error) => InternalServerError(error.reason)
    }
  }


  def addBook(): Action[AnyContent] = Action { implicit request =>
    Ok(views.html.addBook(DataModel.bookForm))
  }

  def addBookForm(): Action[AnyContent] = Action.async { implicit request =>
    accessToken // call the accessToken method
    DataModel.bookForm.bindFromRequest().fold(
      formWithErrors => {
        // Handle form errors
        Future.successful(BadRequest(views.html.addBook(formWithErrors)))
      },
      formData => {
        dataRepository.create(formData).map {
          case Right(book) => Redirect(routes.ApplicationController.index)
          case Left(error) => InternalServerError("Error creating book")
        }
      }
    )
  }



  def accessToken(implicit request: Request[_]): Option[CSRF.Token] = {
    CSRF.getToken
  }
}
